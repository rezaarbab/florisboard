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

import java.util.Locale

/**
 * The set of BCP-47 language tags ML Kit on-device translation supports.
 */
object TranslationLanguages {
    const val DEFAULT_TARGET_TAG = "fa"

    val supportedTags: List<String> = listOf(
        "af", "ar", "be", "bg", "bn", "ca", "cs", "cy", "da", "de",
        "el", "en", "eo", "es", "et", "fa", "fi", "fr", "ga", "gl",
        "gu", "he", "hi", "hr", "ht", "hu", "id", "it", "ja", "kn",
        "ko", "lt", "lv", "mk", "mr", "ms", "mt", "nl", "no", "pl",
        "pt", "ro", "ru", "sk", "sl", "sq", "sv", "sw", "ta", "te",
        "th", "tl", "tr", "uk", "ur", "vi", "zh",
    )

    fun isSupported(tag: String): Boolean {
        return supportedTags.any { it.equals(tag, ignoreCase = true) }
    }

    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(Locale.getDefault()).ifBlank { tag.uppercase(Locale.ROOT) }
    }
}
