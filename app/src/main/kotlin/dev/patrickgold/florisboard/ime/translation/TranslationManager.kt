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

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

sealed interface TranslationUiState {
    data object Hidden : TranslationUiState
    data class Busy(val phase: TranslationPhase) : TranslationUiState
    data class Live(
        val original: String,
        val sourceLanguageTag: String,
        val targetLanguageTag: String,
    ) : TranslationUiState
    data class Failed(val failure: TranslationFailure, val detail: String? = null) : TranslationUiState
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
    ModelUnavailable,
    TextChanged,
}

sealed interface TranslationOutcome {
    data class Success(val text: String, val sourceLanguageTag: String) : TranslationOutcome
    data class Failure(val failure: TranslationFailure, val detail: String? = null) : TranslationOutcome
}

/**
 * Owns the on-device translation pipeline and exposes its progress to the [TranslationBar].
 *
 * Text is split into runs of equal script. Runs which already use the target language's script
 * (e.g. names written in Latin while translating to English) are kept verbatim, everything else
 * gets translated. This keeps mixed-language sentences and proper nouns intact.
 */
class TranslationManager(private val context: Context) {
    private val _state = MutableStateFlow<TranslationUiState>(TranslationUiState.Hidden)
    val state: StateFlow<TranslationUiState> = _state.asStateFlow()
    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live.asStateFlow()
    private val translatorCache = mutableMapOf<String, Translator>()
    private val cacheLock = Mutex()

    fun isLive(): Boolean = _live.value

    fun startLive() {
        _live.value = true
        _state.value = TranslationUiState.Hidden
    }

    fun stopLive() {
        _live.value = false
        _state.value = TranslationUiState.Hidden
    }

    fun showLive(original: String, sourceLanguageTag: String, targetLanguageTag: String) {
        _state.value = TranslationUiState.Live(original, sourceLanguageTag, targetLanguageTag)
    }

    fun showBusy(phase: TranslationPhase) {
        _state.value = TranslationUiState.Busy(phase)
    }

    fun showFailure(failure: TranslationFailure, detail: String?) {
        _state.value = TranslationUiState.Failed(failure, detail)
    }

    fun hide() {
        _state.value = TranslationUiState.Hidden
    }

    suspend fun translateSegments(text: String, targetTag: String): TranslationOutcome {
        val targetLanguage = TranslateLanguage.fromLanguageTag(targetTag)
            ?: return TranslationOutcome.Failure(TranslationFailure.TargetLanguageNotSupported)
        val gmsStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        if (gmsStatus != ConnectionResult.SUCCESS) {
            val reason = GoogleApiAvailability.getInstance().getErrorString(gmsStatus)
            return TranslationOutcome.Failure(
                TranslationFailure.ModelUnavailable,
                detail = "Play services: $gmsStatus ($reason)",
            )
        }
        val targetScripts = ScriptSegmenter.targetScriptsFor(targetTag)
        val out = StringBuilder()
        var position = 0
        var lastSourceTag = targetTag
        for (run in ScriptSegmenter.split(text)) {
            out.append(text, position, run.start)
            position = run.end
            val sourceLanguage = runSourceLanguage(run.text, targetTag)
            if (run.script in targetScripts || run.script == TextScript.OTHER ||
                sourceLanguage == null || sourceLanguage == targetLanguage
            ) {
                out.append(run.text)
                continue
            }
            try {
                out.append(translateRun(run.text, sourceLanguage, targetLanguage))
            } catch (e: TimeoutCancellationException) {
                return TranslationOutcome.Failure(
                    TranslationFailure.ModelUnavailable,
                    detail = "download timed out after ${MODEL_DOWNLOAD_TIMEOUT_MS / 1000}s",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return TranslationOutcome.Failure(
                    TranslationFailure.ModelUnavailable,
                    detail = "${e.javaClass.simpleName}: ${e.message}",
                )
            }
            lastSourceTag = sourceLanguage
        }
        out.append(text, position, text.length)
        return TranslationOutcome.Success(out.toString(), lastSourceTag)
    }

    private suspend fun runSourceLanguage(text: String, targetTag: String): String? {
        val detectedTag = LanguageDetector.detect(text)
        val detected = detectedTag?.let { TranslateLanguage.fromLanguageTag(it) }
        if (detected != null) {
            return detected
        }
        val fallbackTag = if (targetTag.equals("en", ignoreCase = true)) "fa" else "en"
        return TranslateLanguage.fromLanguageTag(fallbackTag)
    }

    private suspend fun translateRun(text: String, sourceLanguage: String, targetLanguage: String): String {
        val translator = translatorFor(sourceLanguage, targetLanguage)
        withTimeout(MODEL_DOWNLOAD_TIMEOUT_MS) {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitResult()
        }
        return translator.translate(text).awaitResult()
    }

    private suspend fun translatorFor(sourceLanguage: String, targetLanguage: String): Translator {
        val key = "$sourceLanguage|$targetLanguage"
        return cacheLock.withLock {
            translatorCache[key] ?: run {
                if (translatorCache.size >= MAX_CACHED_TRANSLATORS) {
                    val oldestKey = translatorCache.keys.first()
                    translatorCache.remove(oldestKey)?.close()
                }
                val translator = Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(sourceLanguage)
                        .setTargetLanguage(targetLanguage)
                        .build()
                )
                translatorCache[key] = translator
                translator
            }
        }
    }

    companion object {
        const val MAX_TEXT_LENGTH = 400

        private const val MAX_CACHED_TRANSLATORS = 4
        private const val MODEL_DOWNLOAD_TIMEOUT_MS = 120_000L
    }
}
