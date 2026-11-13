package com.dataeater.app.builder

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dataeater.app.ocr.OcrLanguage
import com.dataeater.app.ui.theme.DataEaterButton

@Composable
internal fun BuilderOcrSettings(model: BuilderViewModel) {
    val state = model.state
    var languageMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Read scanned PDFs (OCR)", Modifier.weight(1f).padding(top = 12.dp))
        Switch(state.ocrLanguage != null, { model.ocr(if (it) OcrLanguage.ENGLISH else null) },
            enabled = !state.busy, modifier = Modifier.semantics { contentDescription = "Read scanned PDFs (OCR)" })
    }
    state.ocrLanguage?.let { selected ->
        Box {
            DataEaterButton(onClick = { languageMenu = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("OCR language: ${selected.label}") }
            DropdownMenu(expanded = languageMenu && !state.busy, onDismissRequest = { languageMenu = false }) {
                OcrLanguage.entries.forEach { language -> DropdownMenuItem(text = { Text(language.label) }, onClick = { languageMenu = false; model.ocr(language) }) }
            }
        }
        Text("Reads all pages with little selectable text, offline. No AI model is needed. Scanned manuals take longer; you can cancel between pages. Check recognised numbers, units and warnings against the PDF.", style = MaterialTheme.typography.bodySmall)
    }
}
