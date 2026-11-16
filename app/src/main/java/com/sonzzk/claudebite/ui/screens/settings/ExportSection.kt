package com.sonzzk.claudebite.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.sonzzk.claudebite.data.db.FoodEntry
import com.sonzzk.claudebite.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Export the whole food log as a CSV (opens the share sheet: Drive, email, Sheets...). */
@Composable
internal fun ExportSection() {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    SettingsCard("Export", "📤") {
        Text(
            "Your food log as a spreadsheet file (CSV). Everything stays on your phone until you choose where to send it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = {
            scope.launch {
                val entries = c.repository.allFood()
                if (entries.isEmpty()) {
                    status = "Nothing logged yet."
                    return@launch
                }
                val file = withContext(Dispatchers.IO) { writeCsv(context, entries) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Export food log"))
                status = "Exported ${entries.size} entries."
            }
        }) { Text("Export food log (CSV)") }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

internal object Csv {
    fun escape(v: String): String = if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    fun format(entries: List<FoodEntry>): String = buildString {
        appendLine("date,meal,meal_name,food,portion,kcal,protein_g,carbs_g,fat_g,fiber_g,sugar_g,health_score,source")
        entries.forEach { e ->
            appendLine(
                listOf(
                    e.date, e.mealType.name.lowercase(), e.mealName.orEmpty(), e.name, e.portion,
                    e.calories.toString(), e.proteinG.toString(), e.carbsG.toString(), e.fatG.toString(),
                    e.fiberG.toString(), e.sugarG.toString(), e.healthScore?.toString().orEmpty(), e.source.name.lowercase(),
                ).joinToString(",") { escape(it) },
            )
        }
    }
}

private fun writeCsv(context: Context, entries: List<FoodEntry>): File {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "claudebite-food-log.csv")
    file.writeText(Csv.format(entries))
    return file
}
