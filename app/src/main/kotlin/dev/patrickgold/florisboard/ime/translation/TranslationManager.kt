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

import com.google.mlkit.nl.translate.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface TranslationUiState {
    data object Hidden : TranslationUiState
    data class Busy(val phase: TranslationPhase) : TranslationUiState
    data class Ready(
        val original: String,
        val translation: String,
        val sourceLanguageTag: String,
        val targetLanguageTag: String,
        val onApply: (String) -> Boolean,
    ) : TranslationUiState
    data class Failed(val failure: TranslationFailure) : TranslationUiState
}

enum class TranslationPhase {
    DetectingLanguage,
    DownloadingModel,
    Translating,
}

enum class TranslationFailure {
    NoText,
    SourceLanguageNotSupported,
    TargetLanguageNotSupported,
    SameLanguage,
    ModelUnavailable,
    TextChanged,
}

/**
 * Owns the on-device translation pipeline (language detection -> model download -> translation)
 * and exposes its progress to the [TranslationBar].
 */
class TranslationManager {
    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<TranslationUiState>(TranslationUiState.Hidden)
    val state: StateFlow<TranslationUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun toggle(text: String, onApply: (String) -> Boolean) {
        if (_state.value != TranslationUiState.Hidden) {
            dismiss()
        } else {
            translate(text, onApply)
        }
    }

    fun translate(text: String, onApply: (String) -> Boolean) {
        job?.cancel()
        val sourceText = text.trim().take(MAX_TEXT_LENGTH)
        if (sourceText.isEmpty()) {
            _state.value = TranslationUiState.Failed(TranslationFailure.NoText)
            return
        }
        val targetTag = prefs.translation.targetLanguage.get()
        val targetLanguage = TranslateLanguage.fromLanguageTag(targetTag)
        if (targetLanguage == null) {
            _state.value = TranslationUiState.Failed(TranslationFailure.TargetLanguageNotSupported)
            return
        }
        job = scope.launch {
            try {
                _state.value = TranslationUiState.Busy(TranslationPhase.DetectingLanguage)
                val detectedTag = LanguageDetector.detect(sourceText)
                val sourceLanguage = detectedTag?.let { TranslateLanguage.fromLanguageTag(it) }
                if (sourceLanguage == null) {
                    _state.value = TranslationUiState.Failed(TranslationFailure.SourceLanguageNotSupported)
                    return@launch
                }
                if (sourceLanguage == targetLanguage) {
                    _state.value = TranslationUiState.Failed(TranslationFailure.SameLanguage)
                    return@launch
                }

                _state.value = TranslationUiState.Busy(TranslationPhase.DownloadingModel)
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLanguage)
                    .setTargetLanguage(targetLanguage)
                    .build()
                val translator = Translation.getClient(options)
                try {
                    translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitResult()
                    _state.value = TranslationUiState.Busy(TranslationPhase.Translating)
                    val translated = translator.translate(sourceText).awaitResult()
                    _state.value = TranslationUiState.Ready(
                        original = sourceText,
                        translation = translated,
                        sourceLanguageTag = sourceLanguage,
                        targetLanguageTag = targetLanguage,
                        onApply = onApply,
                    )
                } finally {
                    translator.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = TranslationUiState.Failed(TranslationFailure.ModelUnavailable)
            }
        }
    }

    fun apply() {
        val ready = _state.value as? TranslationUiState.Ready ?: return
        _state.value = if (ready.onApply(ready.translation)) {
            TranslationUiState.Hidden
        } else {
            TranslationUiState.Failed(TranslationFailure.TextChanged)
        }
    }

    fun dismiss() {
        job?.cancel()
        job = null
        _state.value = TranslationUiState.Hidden
    }

    companion object {
        const val MAX_TEXT_LENGTH = 400
    }
}
