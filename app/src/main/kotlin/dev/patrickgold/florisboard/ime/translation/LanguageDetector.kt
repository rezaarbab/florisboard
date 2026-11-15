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

import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Thin wrapper around the ML Kit language identification client.
 */
object LanguageDetector {
    private const val UNDETERMINED_LANGUAGE_TAG = "und"

    private val client by lazy {
        LanguageIdentification.getClient()
    }

    /**
     * Returns the BCP-47 language tag of [text], or null if the language could not be determined
     * with sufficient confidence.
     */
    suspend fun detect(text: String): String? {
        if (text.isBlank()) return null
        val tag = try {
            client.identifyLanguage(text).awaitResult()
        } catch (_: Exception) {
            return null
        }
        return tag.takeIf { it != UNDETERMINED_LANGUAGE_TAG }
    }
}

internal suspend fun <T> Task<T>.awaitResult(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resumeWith(Result.success(result))
        }
        addOnFailureListener { exception ->
            continuation.resumeWith(Result.failure(exception))
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
}
