package com.nuvio.app.features.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue

internal object PlayerSubtitleUtils {
    fun normalizeLanguageCode(lang: String): String =
        SubtitleLanguageMatching.normalizeLanguageCode(lang)

    fun matchesLanguageCode(language: String?, target: String): Boolean =
        SubtitleLanguageMatching.matchesLanguageCode(language, target)

    fun detectTrackLanguageVariant(language: String?, name: String?, trackId: String?): String =
        SubtitleLanguageMatching.detectTrackLanguageVariant(language, name, trackId)

    internal val BRAZILIAN_TAGS = SubtitleLanguageMatching.BRAZILIAN_TAGS
    internal val EUROPEAN_PT_TAGS = SubtitleLanguageMatching.EUROPEAN_PT_TAGS
    internal val LATINO_TAGS = SubtitleLanguageMatching.LATINO_TAGS
    internal val CASTILIAN_TAGS = SubtitleLanguageMatching.CASTILIAN_TAGS

    fun mimeTypeFromUrl(url: String): String {
        val normalizedPath = url
            .substringBefore('#')
            .substringBefore('?')
            .trimEnd('/')
            .lowercase()

        return when {
            normalizedPath.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            normalizedPath.endsWith(".vtt") || normalizedPath.endsWith(".webvtt") -> MimeTypes.TEXT_VTT
            normalizedPath.endsWith(".ass") || normalizedPath.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            normalizedPath.endsWith(".ttml") || normalizedPath.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    fun sniffSubtitleMimeType(rawText: String, sourceUrl: String = ""): String {
        val text = rawText.replace("\uFEFF", "").trimStart()
        if (text.isEmpty()) return mimeTypeFromUrl(sourceUrl)

        if (text.startsWith("WEBVTT", ignoreCase = true)) {
            return MimeTypes.TEXT_VTT
        }

        val head = text.take(4_000)
        if (
            head.startsWith("[Script Info]", ignoreCase = true) ||
            head.contains("[V4+ Styles]", ignoreCase = true) ||
            head.contains("[V4 Styles]", ignoreCase = true) ||
            Regex("""(?im)^\s*Dialogue:""").containsMatchIn(head)
        ) {
            return MimeTypes.TEXT_SSA
        }

        val lowerHead = head.lowercase()
        if (
            (lowerHead.startsWith("<?xml") || lowerHead.contains("<tt ")) &&
            (lowerHead.contains("ttml") || lowerHead.contains(":tt") || lowerHead.contains("<tt "))
        ) {
            return MimeTypes.APPLICATION_TTML
        }

        if (
            Regex(
                """(?m)^\d+\s*\r?\n\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}"""
            ).containsMatchIn(text.take(800)) ||
            Regex(
                """(?m)^\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}"""
            ).containsMatchIn(text.take(400))
        ) {
            return MimeTypes.APPLICATION_SUBRIP
        }

        return mimeTypeFromUrl(sourceUrl)
    }

    fun sidecarMimeCandidates(rawText: String, sourceUrl: String): List<String> {
        val sniffed = sniffSubtitleMimeType(rawText, sourceUrl)
        val fromUrl = mimeTypeFromUrl(sourceUrl)
        return linkedSetOf(
            sniffed,
            fromUrl,
            MimeTypes.APPLICATION_SUBRIP,
            MimeTypes.TEXT_VTT,
            MimeTypes.TEXT_SSA,
            MimeTypes.APPLICATION_TTML
        ).toList()
    }

    /**
     * Puts a text cue back in the middle: drops the position, width and alignment the caption file
     * asked for, so it's laid out like an ordinary subtitle. Used for YouTube's captions, which
     * otherwise hug the left edge and get cut off.
     */
    fun centerCue(cue: Cue): Cue {
        if (cue.bitmap != null || cue.verticalType != Cue.TYPE_UNSET) return cue
        val builder = cue.buildUpon()
            .setPosition(Cue.DIMEN_UNSET)
            .setPositionAnchor(Cue.TYPE_UNSET)
            .setSize(Cue.DIMEN_UNSET)
            .setTextAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
        // Captions in TTML can also align inside the text itself.
        val text = cue.text
        if (text is android.text.Spanned) {
            val spans = text.getSpans(0, text.length, android.text.style.AlignmentSpan::class.java)
            if (spans.isNotEmpty()) {
                val copy = android.text.SpannableStringBuilder(text)
                spans.forEach { copy.removeSpan(it) }
                builder.setText(copy)
            }
        }
        return builder.build()
    }
}
