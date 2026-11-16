package com.sonzzk.claudebite.ui.screens.log

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonzzk.claudebite.data.db.MealType
import com.sonzzk.claudebite.ui.LocalAppContainer
import com.sonzzk.claudebite.ui.appViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Log Food flow: choose how to log -> Claude-chan analyzes -> review & edit -> saved.
 * Shown without the bottom bar, so it handles the system bar/keyboard insets itself.
 */
@Composable
fun LogFoodScreen(initialMeal: MealType?, date: String? = null, onDone: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val vm = appViewModel { c -> LogFoodViewModel(c.coach, c.repository, appContext, initialMeal, date) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var labelMode by rememberSaveable { mutableStateOf(false) }
    val stage by vm.stage.collectAsStateWithLifecycle()
    val mealType by vm.mealType.collectAsStateWithLifecycle()
    val recent by vm.recentFoods.collectAsStateWithLifecycle()
    val recentMeals by vm.recentMeals.collectAsStateWithLifecycle()
    val targets by vm.targets.collectAsStateWithLifecycle()

    val profiles = LocalAppContainer.current.profiles
    val outfitFlow = remember(profiles) { profiles.progression.map { it.equippedOutfit } }
    val outfit by outfitFlow.collectAsStateWithLifecycle(initialValue = "classic")

    var hint by rememberSaveable { mutableStateOf("") }
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }
    var cameraDenied by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // ---- Photo launchers ----
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            if (labelMode) vm.analyzeLabel(uri, hint) else vm.analyzePhoto(uri, hint)
        }
        labelMode = false
    }
    fun launchCamera() {
        val uri = createCaptureUri(context)
        pendingCameraUri = uri
        takePicture.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraDenied = !granted
        if (granted) launchCamera()
    }
    // Up to 4 photos of the same meal (e.g. plate + packaging) are analysed together.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        if (uris.isNotEmpty()) vm.analyzePhotos(uris, hint)
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) vm.analyzeText(text)
    }
    fun startVoice() {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "What did you eat?")
        }
        runCatching { voice.launch(intent) }
    }
    fun scanBarcode() {
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context)
            .startScan()
            .addOnSuccessListener { code -> code.rawValue?.let(vm::lookupBarcode) }
    }
    fun scanLabel() {
        labelMode = true
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    // Back from inner steps returns to Choose instead of leaving the screen.
    BackHandler(enabled = stage !is LogStage.Choose && stage !is LogStage.Saved) {
        if (stage is LogStage.Analyzing) vm.cancelAnalysis() else vm.backToChoose()
    }
    LaunchedEffect(stage) {
        if (stage is LogStage.Saved) {
            delay(1400)
            onDone()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val atStart = stage is LogStage.Choose
            IconButton(onClick = {
                when (stage) {
                    is LogStage.Choose, is LogStage.Saved -> onDone()
                    is LogStage.Analyzing -> vm.cancelAnalysis()
                    else -> vm.backToChoose()
                }
            }) {
                Icon(
                    if (atStart) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = if (atStart) "Close" else "Back",
                )
            }
            Text(
                when (stage) {
                    is LogStage.Review -> "Check my guess"
                    is LogStage.Analyzing -> "Analyzing..."
                    else -> "Log food"
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = stage,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.92f)) togetherWith fadeOut()
                },
                label = "logStage",
            ) { s ->
                when (s) {
                    is LogStage.Choose -> ChooseContent(
                        mealType = mealType,
                        onMealType = vm::setMealType,
                        outfit = outfit,
                        hint = hint,
                        onHint = { hint = it },
                        cameraDenied = cameraDenied,
                        quickAddOpen = quickAddOpen,
                        onQuickAddOpen = { quickAddOpen = it },
                        recent = recent,
                        recentMeals = recentMeals,
                        onSnap = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
                        },
                        onPick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onDescribe = vm::analyzeText,
                        onQuickAdd = vm::quickAdd,
                        onRelog = vm::relog,
                        onToggleFavorite = vm::toggleFavorite,
                        onRelogMeal = vm::relogMeal,
                        onToggleMealFavorite = vm::toggleMealFavorite,
                        onScanBarcode = ::scanBarcode,
                        onSearch = { searchOpen = true },
                        onScanLabel = ::scanLabel,
                        onVoice = ::startVoice,
                    )
                    is LogStage.Analyzing -> AnalyzingContent(s.photoPath, outfit, onCancel = vm::cancelAnalysis)
                    is LogStage.Review -> ReviewContent(
                        review = s,
                        mealType = mealType,
                        onMealType = vm::setMealType,
                        targets = targets,
                        outfit = outfit,
                        onMultiplier = vm::setMultiplier,
                        onRemove = vm::removeItem,
                        onAddItem = vm::addItem,
                        onMealName = vm::setMealName,
                        onSearch = { searchOpen = true },
                        onScanBarcode = ::scanBarcode,
                        onSave = vm::save,
                        onRetry = vm::retry,
                    )
                    is LogStage.Saved -> SavedContent(outfit)
                    is LogStage.Failed -> FailedContent(
                        failure = s,
                        outfit = outfit,
                        onRetry = vm::retry,
                        onBack = vm::backToChoose,
                        onQuickAdd = {
                            quickAddOpen = true
                            vm.backToChoose()
                        },
                    )
                }
            }
        }
    }
    if (searchOpen) {
        FoodSearchSheet(
            query = query,
            results = results,
            searching = searching,
            onQuery = vm::setQuery,
            onPick = { food, grams ->
                vm.addProduct(food, grams)
                searchOpen = false
            },
            onDismiss = { searchOpen = false },
        )
    }
}

/** A FileProvider uri in cacheDir/photos/ (see res/xml/file_paths.xml) for the camera to write into. */
private fun createCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
