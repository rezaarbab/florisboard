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

package dev.patrickgold.florisboard.app.settings.translation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.components.FlorisScreenNg
import dev.patrickgold.florisboard.app.components.ScrollableScreenColumn
import dev.patrickgold.florisboard.app.components.SegmentedListColumn
import dev.patrickgold.florisboard.ime.translation.TranslationLanguages
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@Composable
fun TranslationScreen() {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val targetTag = prefs.translation.targetLanguage.collectAsState().value
    var showTargetLanguageDialog by remember { mutableStateOf(false) }

    FlorisScreenNg(
        title = stringRes(R.string.settings__translation__title),
        previewFieldVisible = true,
    ) { contentPadding ->
        ScrollableScreenColumn(contentPadding) {
            SegmentedListColumn {
                val count = 2
                SwitchPreference(
                    prefs.translation.enabled,
                    shapes = ListItemDefaults.segmentedShapes(0, count),
                    title = stringRes(R.string.pref__translation__enabled__label),
                    summary = stringRes(R.string.pref__translation__enabled__summary),
                )
                Preference(
                    shapes = ListItemDefaults.segmentedShapes(1, count),
                    icon = Icons.Default.Translate,
                    title = stringRes(R.string.pref__translation__target_language__label),
                    summary = TranslationLanguages.displayName(targetTag),
                    onClick = { showTargetLanguageDialog = true },
                )
            }
        }
    }

    if (showTargetLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showTargetLanguageDialog = false },
            title = { Text(text = stringRes(R.string.translation__target_language__dialog_title)) },
            text = {
                Column {
                    Text(text = stringRes(R.string.translation__target_language__download_notice))
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(TranslationLanguages.supportedTags) { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            prefs.translation.targetLanguage.set(tag)
                                        }
                                        showTargetLanguageDialog = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = TranslationLanguages.displayName(tag),
                                    fontWeight = if (tag == targetTag) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTargetLanguageDialog = false }) {
                    Text(text = stringRes(R.string.action__cancel))
                }
            },
        )
    }
}
