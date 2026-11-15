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

enum class TextScript {
    LATIN,
    ARABIC,
    CYRILLIC,
    GREEK,
    HEBREW,
    HAN,
    KANA,
    HANGUL,
    THAI,
    DEVANAGARI,
    BENGALI,
    TAMIL,
    TELUGU,
    KANNADA,
    GUJARATI,
    OTHER,
}

data class ScriptRun(
    val text: String,
    val script: TextScript,
    val start: Int,
    val end: Int,
)

/**
 * Splits text into runs of the same script so that parts already written in the target
 * language's script (names, brand names, loanwords) are never sent to the translator.
 */
object ScriptSegmenter {
    fun targetScriptsFor(targetTag: String): Set<TextScript> {
        return when (targetTag.lowercase(Locale.ROOT)) {
            "ar", "fa", "ur" -> setOf(TextScript.ARABIC)
            "be", "bg", "mk", "ru", "uk" -> setOf(TextScript.CYRILLIC)
            "el" -> setOf(TextScript.GREEK)
            "he" -> setOf(TextScript.HEBREW)
            "zh" -> setOf(TextScript.HAN)
            "ja" -> setOf(TextScript.HAN, TextScript.KANA)
            "ko" -> setOf(TextScript.HANGUL)
            "th" -> setOf(TextScript.THAI)
            "hi", "mr" -> setOf(TextScript.DEVANAGARI)
            "bn" -> setOf(TextScript.BENGALI)
            "ta" -> setOf(TextScript.TAMIL)
            "te" -> setOf(TextScript.TELUGU)
            "kn" -> setOf(TextScript.KANNADA)
            "gu" -> setOf(TextScript.GUJARATI)
            else -> setOf(TextScript.LATIN)
        }
    }

    fun split(text: String): List<ScriptRun> {
        val runs = mutableListOf<ScriptRun>()
        var runStart = -1
        var runScript = TextScript.OTHER
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val script = if (char.isLetter()) classify(char) else null
            if (script != null) {
                if (runStart < 0) {
                    runStart = index
                    runScript = script
                } else if (script != runScript) {
                    runs.add(ScriptRun(text.substring(runStart, index), runScript, runStart, index))
                    runStart = index
                    runScript = script
                }
            } else if (runStart >= 0 && isRunContinuation(char)) {
                // joiners and combining marks stay within the current run
            } else if (runStart >= 0) {
                runs.add(ScriptRun(text.substring(runStart, index), runScript, runStart, index))
                runStart = -1
            }
            index++
        }
        if (runStart >= 0) {
            runs.add(ScriptRun(text.substring(runStart), runScript, runStart, text.length))
        }
        return runs
    }

    private fun classify(char: Char): TextScript {
        return when (char.code) {
            in 0x0041..0x005A, in 0x0061..0x007A, in 0x00C0..0x024F, in 0x1E00..0x1EFF -> TextScript.LATIN
            in 0x0600..0x06FF, in 0x0750..0x077F, in 0x08A0..0x08FF,
            in 0xFB50..0xFDFF, in 0xFE70..0xFEFF -> TextScript.ARABIC
            in 0x0590..0x05FF -> TextScript.HEBREW
            in 0x0370..0x03FF, in 0x1F00..0x1FFF -> TextScript.GREEK
            in 0x0400..0x052F -> TextScript.CYRILLIC
            in 0x0900..0x097F -> TextScript.DEVANAGARI
            in 0x0980..0x09FF -> TextScript.BENGALI
            in 0x0A80..0x0AFF -> TextScript.GUJARATI
            in 0x0B80..0x0BFF -> TextScript.TAMIL
            in 0x0C00..0x0C7F -> TextScript.TELUGU
            in 0x0C80..0x0CFF -> TextScript.KANNADA
            in 0x0E00..0x0E7F -> TextScript.THAI
            in 0x3040..0x30FF -> TextScript.KANA
            in 0x3400..0x4DBF, in 0x4E00..0x9FFF -> TextScript.HAN
            in 0x1100..0x11FF, in 0xAC00..0xD7AF -> TextScript.HANGUL
            else -> TextScript.OTHER
        }
    }

    private fun isRunContinuation(char: Char): Boolean {
        if (char == '\u200C' || char == '\u200D') {
            return true
        }
        return when (Character.getType(char)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt() -> true
            else -> false
        }
    }
}
