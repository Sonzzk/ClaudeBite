package com.sonzzk.claudebite.ui.screens.log

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonzzk.claudebite.ai.AiCoach
import com.sonzzk.claudebite.ai.AiError
import com.sonzzk.claudebite.ai.AnalyzedItem
import com.sonzzk.claudebite.ai.FoodAnalysis
import com.sonzzk.claudebite.ai.ImageCompressor
import com.sonzzk.claudebite.data.HealthRepository
import com.sonzzk.claudebite.data.db.EntrySource
import com.sonzzk.claudebite.data.db.FoodEntry
import com.sonzzk.claudebite.data.db.MealType
import com.sonzzk.claudebite.data.prefs.DailyTargets
import com.sonzzk.claudebite.domain.NutritionCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime
import kotlin.math.roundToInt

/** An item on the review screen. [multiplier] is the portion slider (0.25x - 3x) applied to [base]. */
data class EditableItem(val key: Long, val base: AnalyzedItem, val multiplier: Float = 1f) {
    /** The item with every number scaled by the portion slider. */
    val scaled: AnalyzedItem
        get() = if (multiplier == 1f) base else base.copy(
            portion = "${base.portion} ×${"%.2f".format(multiplier).trimEnd('0').trimEnd('.', ',')}",
            calories = (base.calories * multiplier).roundToInt(),
            proteinG = base.proteinG * multiplier,
            carbsG = base.carbsG * multiplier,
            fatG = base.fatG * multiplier,
            fiberG = base.fiberG * multiplier,
            sugarG = base.sugarG * multiplier,
        )
}

/** The steps of the Log Food flow. */
sealed interface LogStage {
    data object Choose : LogStage
    data class Analyzing(val photoPath: String?) : LogStage
    data class Review(
        val analysis: FoodAnalysis,
        val items: List<EditableItem>,
        val source: EntrySource,
        val photoPath: String?,
    ) : LogStage {
        val total: List<AnalyzedItem> get() = items.map { it.scaled }
    }
    data object Saved : LogStage
    data class Failed(val message: String, val noApiKey: Boolean) : LogStage
}

/** What we asked Claude last time, so "Try again" can repeat it without re-compressing the photo. */
internal sealed interface AnalysisRequest {
    class Photo(val bytes: ByteArray, val photoPath: String, val hint: String?) : AnalysisRequest
    class Text(val description: String) : AnalysisRequest
}

/**
 * Drives the Log Food screen: Choose -> Analyzing -> Review -> Saved.
 * Recent foods and Quick add never call Claude, so they cost zero tokens.
 */
class LogFoodViewModel(
    private val coach: AiCoach,
    private val repository: HealthRepository,
    private val appContext: Context,
    initialMeal: MealType?,
) : ViewModel() {

    private val _stage = MutableStateFlow<LogStage>(LogStage.Choose)
    val stage: StateFlow<LogStage> = _stage.asStateFlow()

    private val _mealType = MutableStateFlow(initialMeal ?: mealForNow())
    val mealType: StateFlow<MealType> = _mealType.asStateFlow()

    val recentFoods: StateFlow<List<FoodEntry>> =
        repository.recentFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Daily targets, for the macro bars on the review screen. */
    val targets: StateFlow<DailyTargets?> = repository.profiles.profile
        .map { NutritionCalculator.targets(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var lastRequest: AnalysisRequest? = null
    private var analyzeJob: Job? = null
    private var nextKey = 0L

    fun setMealType(meal: MealType) { _mealType.value = meal }

    /** Compresses the photo (off the main thread), keeps a thumbnail copy, then asks Claude. */
    fun analyzePhoto(uri: Uri, hint: String?) {
        analyzeJob?.cancel()
        _stage.value = LogStage.Analyzing(photoPath = null)
        analyzeJob = viewModelScope.launch {
            val prepared = try {
                withContext(Dispatchers.IO) {
                    val bytes = ImageCompressor.fromUri(appContext, uri)
                    val dir = File(appContext.filesDir, "photos").apply { mkdirs() }
                    val file = File(dir, "${System.currentTimeMillis()}.jpg")
                    file.writeBytes(bytes)
                    // The camera's full-size temp file is no longer needed.
                    if (uri.authority == "${appContext.packageName}.fileprovider") {
                        runCatching { appContext.contentResolver.delete(uri, null, null) }
                    }
                    bytes to file.absolutePath
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _stage.value = LogStage.Failed("I couldn't open that photo. Could you try another one?", noApiKey = false)
                return@launch
            }
            val request = AnalysisRequest.Photo(prepared.first, prepared.second, hint?.takeIf { it.isNotBlank() })
            lastRequest = request
            runAnalysis(request)
        }
    }

    fun analyzeText(description: String) {
        if (description.isBlank()) return
        val request = AnalysisRequest.Text(description.trim())
        lastRequest = request
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch { runAnalysis(request) }
    }

    /** "Try again": repeats the last request (the photo is not compressed twice). */
    fun retry() {
        val request = lastRequest ?: run { _stage.value = LogStage.Choose; return }
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch { runAnalysis(request) }
    }

    fun cancelAnalysis() {
        analyzeJob?.cancel()
        _stage.value = LogStage.Choose
    }

    fun backToChoose() { _stage.value = LogStage.Choose }

    private suspend fun runAnalysis(request: AnalysisRequest) {
        val photoPath = (request as? AnalysisRequest.Photo)?.photoPath
        _stage.value = LogStage.Analyzing(photoPath)
        try {
            val context = repository.coachContext()
            val analysis = when (request) {
                is AnalysisRequest.Photo -> coach.analyzeFoodPhoto(request.bytes, request.hint, context)
                is AnalysisRequest.Text -> coach.analyzeFoodText(request.description, context)
            }
            _stage.value = LogStage.Review(
                analysis = analysis,
                items = analysis.items.map { EditableItem(nextKey++, it) },
                source = if (request is AnalysisRequest.Photo) EntrySource.PHOTO else EntrySource.TEXT,
                photoPath = photoPath,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiError) {
            _stage.value = LogStage.Failed(e.message ?: "Something went wrong.", noApiKey = e is AiError.NoApiKey)
        } catch (e: Exception) {
            _stage.value = LogStage.Failed("Oops, something went wrong: ${e.message ?: "unknown error"}", noApiKey = false)
        }
    }

    // ---- Review editing ----

    private inline fun editReview(change: (LogStage.Review) -> LogStage.Review) {
        _stage.update { if (it is LogStage.Review) change(it) else it }
    }

    fun setMultiplier(key: Long, multiplier: Float) = editReview { r ->
        r.copy(items = r.items.map { if (it.key == key) it.copy(multiplier = multiplier) else it })
    }

    fun removeItem(key: Long) = editReview { r -> r.copy(items = r.items.filterNot { it.key == key }) }

    fun addItem(name: String, calories: Int, protein: Float, carbs: Float, fat: Float) = editReview { r ->
        val item = AnalyzedItem(name, "1 serving", calories, protein, carbs, fat, fiberG = 0f, sugarG = 0f)
        r.copy(items = r.items + EditableItem(nextKey++, item))
    }

    fun save() {
        val review = _stage.value as? LogStage.Review ?: return
        if (review.items.isEmpty()) return
        viewModelScope.launch {
            repository.logAnalysis(review.analysis, review.total, _mealType.value, review.source, review.photoPath)
            _stage.value = LogStage.Saved
        }
    }

    // ---- Zero-token paths ----

    fun quickAdd(name: String, calories: Int, protein: Float, carbs: Float, fat: Float) {
        viewModelScope.launch {
            repository.logManual(
                FoodEntry(
                    date = repository.today(), timestamp = System.currentTimeMillis(), mealType = _mealType.value,
                    name = name.ifBlank { "Food" }, portion = "1 serving", calories = calories,
                    proteinG = protein, carbsG = carbs, fatG = fat, source = EntrySource.MANUAL,
                )
            )
            _stage.value = LogStage.Saved
        }
    }

    fun relog(entry: FoodEntry) {
        viewModelScope.launch {
            repository.relog(entry, _mealType.value)
            _stage.value = LogStage.Saved
        }
    }

    fun toggleFavorite(entry: FoodEntry) {
        viewModelScope.launch { repository.toggleFavorite(entry) }
    }

    private companion object {
        fun mealForNow(): MealType {
            val hour = LocalTime.now().hour
            return when (hour) {
                in 4..10 -> MealType.BREAKFAST
                in 11..14 -> MealType.LUNCH
                in 17..21 -> MealType.DINNER
                else -> MealType.SNACK
            }
        }
    }
}
