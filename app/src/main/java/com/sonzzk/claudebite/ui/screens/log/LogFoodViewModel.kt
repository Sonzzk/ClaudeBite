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
import com.sonzzk.claudebite.data.food.CommonFoods
import com.sonzzk.claudebite.data.food.FoodProduct
import com.sonzzk.claudebite.data.food.OpenFoodFacts
import com.sonzzk.claudebite.data.db.EntrySource
import com.sonzzk.claudebite.data.db.FoodEntry
import com.sonzzk.claudebite.data.db.MealType
import com.sonzzk.claudebite.data.prefs.DailyTargets
import com.sonzzk.claudebite.domain.GroupedMeal
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
        /** Editable name for the whole meal; used when there is more than one item. */
        val mealName: String = analysis.mealName,
        val saving: Boolean = false,
    ) : LogStage {
        val total: List<AnalyzedItem> get() = items.map { it.scaled }
    }
    data object Saved : LogStage
    data class Failed(val message: String, val noApiKey: Boolean) : LogStage
}

/** What we asked Claude last time, so "Try again" can repeat it without re-compressing the photo. */
internal sealed interface AnalysisRequest {
    class Photo(val bytes: List<ByteArray>, val photoPath: String, val hint: String?) : AnalysisRequest
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
    date: String? = null,
) : ViewModel() {

    /** The day this log goes to (today unless the user picked an earlier day on Home). */
    val logDate: String = date ?: repository.today()

    // ---- Food search (built-in list + recent foods instantly, Open Food Facts after a short pause) ----
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _online = MutableStateFlow<List<FoodProduct>>(emptyList())
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()
    private var searchJob: Job? = null

    val searchResults: StateFlow<List<FoodProduct>> = kotlinx.coroutines.flow.combine(_query, _online, repository.recentFoods()) { q, online, recent ->
        if (q.isBlank()) return@combine emptyList()
        val words = q.lowercase().split(' ').filter { it.isNotBlank() }
        val fromRecent = recent.filter { e -> words.all { it in e.name.lowercase() } }.take(4).map { it.toProduct() }
        (fromRecent + CommonFoods.search(q) + online).distinctBy { it.displayName.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        _query.value = q.take(60)
        searchJob?.cancel()
        _online.value = emptyList()
        if (q.trim().length < 3) { _searching.value = false; return }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _searching.value = true
            _online.value = OpenFoodFacts.search(q)
            _searching.value = false
        }
    }

    private val _stage = MutableStateFlow<LogStage>(LogStage.Choose)
    val stage: StateFlow<LogStage> = _stage.asStateFlow()

    private val _mealType = MutableStateFlow(initialMeal ?: mealForNow())
    val mealType: StateFlow<MealType> = _mealType.asStateFlow()

    val recentFoods: StateFlow<List<FoodEntry>> =
        repository.recentFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentMeals: StateFlow<List<GroupedMeal>> =
        repository.recentMeals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Daily targets, for the macro bars on the review screen. */
    val targets: StateFlow<DailyTargets?> = repository.profiles.profile
        .map { NutritionCalculator.targets(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var lastRequest: AnalysisRequest? = null
    private var analyzeJob: Job? = null
    private var nextKey = 0L

    fun setMealType(meal: MealType) { _mealType.value = meal }

    /** Compresses the photo (off the main thread), keeps a thumbnail copy, then asks Claude. */
    fun analyzePhoto(uri: Uri, hint: String?) = analyzePhotos(listOf(uri), hint)

    /** "Scan label": reads a nutrition facts label exactly. */
    fun analyzeLabel(uri: Uri, hint: String?) =
        analyzePhotos(listOf(uri), listOfNotNull(com.sonzzk.claudebite.ai.Prompts.LABEL_HINT, hint?.takeIf { it.isNotBlank() }).joinToString(" "))

    /** One or more photos of the same meal (e.g. plate + packaging). */
    fun analyzePhotos(uris: List<Uri>, hint: String?) {
        if (uris.isEmpty()) return
        analyzeJob?.cancel()
        _stage.value = LogStage.Analyzing(photoPath = null)
        analyzeJob = viewModelScope.launch {
            val prepared = try {
                withContext(Dispatchers.IO) {
                    val dir = File(appContext.filesDir, "photos").apply { mkdirs() }
                    var firstPath: String? = null
                    val all = uris.take(4).mapIndexed { i, uri ->
                        val bytes = ImageCompressor.fromUri(appContext, uri)
                        if (i == 0) {
                            val file = File(dir, "${System.currentTimeMillis()}.jpg")
                            file.writeBytes(bytes)
                            firstPath = file.absolutePath
                        }
                        // The camera's full-size temp file is no longer needed.
                        if (uri.authority == "${appContext.packageName}.fileprovider") {
                            runCatching { appContext.contentResolver.delete(uri, null, null) }
                        }
                        bytes
                    }
                    all to firstPath!!
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
                is AnalysisRequest.Photo -> coach.analyzeFoodPhotos(request.bytes, request.hint, context)
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

    fun setMealName(name: String) = editReview { r -> r.copy(mealName = name.take(60)) }

    /** Saves the reviewed items as one meal. Ignores repeat taps while a save is running. */
    fun save() {
        val review = _stage.value as? LogStage.Review ?: return
        if (review.items.isEmpty() || review.saving) return
        _stage.value = review.copy(saving = true)
        viewModelScope.launch {
            repository.logAnalysis(review.analysis, review.total, _mealType.value, review.source, review.photoPath, review.mealName, logDate)
            _stage.value = LogStage.Saved
        }
    }

    // ---- Zero-token paths ----

    fun quickAdd(name: String, calories: Int, protein: Float, carbs: Float, fat: Float) {
        viewModelScope.launch {
            repository.logManual(
                FoodEntry(
                    date = logDate, timestamp = System.currentTimeMillis(), mealType = _mealType.value,
                    name = name.ifBlank { "Food" }, portion = "1 serving", calories = calories,
                    proteinG = protein, carbsG = carbs, fatG = fat, source = EntrySource.MANUAL,
                ),
                logDate,
            )
            _stage.value = LogStage.Saved
        }
    }

    fun relog(entry: FoodEntry) {
        viewModelScope.launch {
            repository.relog(entry, _mealType.value, logDate)
            _stage.value = LogStage.Saved
        }
    }

    fun relogMeal(meal: GroupedMeal) {
        viewModelScope.launch {
            repository.relogMeal(meal, _mealType.value, logDate)
            _stage.value = LogStage.Saved
        }
    }

    fun toggleMealFavorite(meal: GroupedMeal) {
        viewModelScope.launch { repository.setMealFavorite(meal.groupId, !meal.isFavorite) }
    }

    // ---- Database foods & barcodes (zero tokens) ----

    /** Starts a review with one database food, or adds it to the review that's already open. */
    fun addProduct(product: FoodProduct, grams: Float) {
        val item = product.toItem(grams)
        val current = _stage.value
        if (current is LogStage.Review) {
            _stage.value = current.copy(items = current.items + EditableItem(nextKey++, item))
            return
        }
        val score = product.healthScore()
        val analysis = FoodAnalysis(
            items = listOf(item),
            healthScore = score,
            grade = com.sonzzk.claudebite.ai.dto.gradeForScore(score),
            comment = localComment(score),
            tip = "",
            confidence = 1f,
            modelUsed = "local",
            mealName = product.name,
        )
        _stage.value = LogStage.Review(analysis, listOf(EditableItem(nextKey++, item)), EntrySource.MANUAL, photoPath = null)
    }

    fun lookupBarcode(code: String) {
        analyzeJob?.cancel()
        val previous = _stage.value as? LogStage.Review
        _stage.value = LogStage.Analyzing(photoPath = null)
        analyzeJob = viewModelScope.launch {
            val product = OpenFoodFacts.byBarcode(code)
            if (product == null) {
                _stage.value = previous ?: LogStage.Failed(
                    "I couldn't find barcode $code in the food database. Try \"Scan label\" instead!", noApiKey = false,
                )
                return@launch
            }
            if (previous != null) _stage.value = previous
            addProduct(product, product.servingG)
        }
    }

    private fun localComment(score: Int) = when {
        score >= 8 -> "Ooh, a great pick! Logged straight from the food database, zero tokens~"
        score >= 5 -> "Logged from the food database! Nice and easy~"
        else -> "A treat! Totally fine. Enjoy it~"
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

/** A logged food as a searchable product (numbers scaled back to per 100 g when the portion is known). */
internal fun FoodEntry.toProduct(): FoodProduct {
    val grams = Regex("""(\d+(?:[.,]\d+)?)\s*g\b""").find(portion)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull()
    return if (grams != null && grams > 0f) {
        val f = 100f / grams
        FoodProduct(name, null, calories * f, proteinG * f, carbsG * f, fatG * f, fiberG * f, sugarG * f, grams, portion, FoodProduct.Source.RECENT)
    } else {
        // Unknown weight: treat the logged portion as "100 g" so 1x = what they logged before.
        FoodProduct(name, null, calories.toFloat(), proteinG, carbsG, fatG, fiberG, sugarG, 100f, portion.ifBlank { "1 serving" }, FoodProduct.Source.RECENT)
    }
}
