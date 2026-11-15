/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.translation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.translationManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import java.util.Locale

private const val MAX_DISPLAY_LENGTH = 160

@Composable
fun TranslationBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val manager by context.translationManager()
    val enabled by prefs.translation.enabled.collectAsState()
    val uiState by manager.state.collectAsState()

    if (!enabled || uiState is TranslationUiState.Hidden) return

    SnyggColumn(
        elementName = FlorisImeUi.Smartbar.elementName,
        modifier = modifier.fillMaxWidth(),
    ) {
        when (val state = uiState) {
            is TranslationUiState.Busy -> TranslationBarRow(
                text = stringRes(when (state.phase) {
                    TranslationPhase.DetectingLanguage -> R.string.translation__bar__detecting_language
                    TranslationPhase.DownloadingModel -> R.string.translation__bar__downloading_model
                    TranslationPhase.Translating -> R.string.translation__bar__translating
                }),
                onDismiss = manager::dismiss,
            )
            is TranslationUiState.Ready -> TranslationBarRow(
                text = state.translation.elide(),
                caption = "${state.sourceLanguageTag.uppercase(Locale.ROOT)} → " +
                    "${state.targetLanguageTag.uppercase(Locale.ROOT)} · ${state.original.elide()}",
                onApply = manager::apply,
                onDismiss = manager::dismiss,
            )
            is TranslationUiState.Failed -> TranslationBarRow(
                text = stringRes(when (state.failure) {
                    TranslationFailure.NoText -> R.string.translation__failure__no_text
                    TranslationFailure.SourceLanguageNotSupported -> R.string.translation__failure__source_language
                    TranslationFailure.TargetLanguageNotSupported -> R.string.translation__failure__target_language
                    TranslationFailure.SameLanguage -> R.string.translation__failure__same_language
                    TranslationFailure.ModelUnavailable -> R.string.translation__failure__model_unavailable
                    TranslationFailure.TextChanged -> R.string.translation__failure__text_changed
                }),
                onDismiss = manager::dismiss,
            )
            TranslationUiState.Hidden -> Unit
        }
    }
}

@Composable
private fun TranslationBarRow(
    text: String,
    caption: String? = null,
    onApply: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    SnyggRow(
        elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FlorisImeSizing.smartbarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            SnyggText(
                elementName = FlorisImeUi.SmartbarCandidateWordText.elementName,
                text = text,
            )
            if (caption != null) {
                SnyggText(
                    elementName = FlorisImeUi.SmartbarCandidateWordText.elementName,
                    text = caption,
                )
            }
        }
        if (onApply != null) {
            BarIconButton(onClick = onApply) {
                SnyggIcon(imageVector = Icons.Default.Check)
            }
        }
        BarIconButton(onClick = onDismiss) {
            SnyggIcon(imageVector = Icons.Default.Close)
        }
    }
}

@Composable
private fun RowScope.BarIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    SnyggIconButton(
        elementName = FlorisImeUi.SmartbarActionKey.elementName,
        modifier = Modifier
            .height(FlorisImeSizing.smartbarHeight)
            .aspectRatio(1f),
        onClick = onClick,
        content = content,
    )
}

private fun String.elide(): String {
    val singleLine = replace('\n', ' ')
    return if (singleLine.length <= MAX_DISPLAY_LENGTH) {
        singleLine
    } else {
        singleLine.take(MAX_DISPLAY_LENGTH - 1) + "…"
    }
}
