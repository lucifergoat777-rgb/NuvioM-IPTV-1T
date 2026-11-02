package com.nuvio.app.youtube

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Puts YouTube's sound tracks in order. Its manifest names each track's language like "en-US (original)" or
 * "es-US (dubbed-auto)": not a language code, so players list every track as "Unknown", can't tell English from
 * the rest, and just play whichever comes first (often a computer-made dub). Here each track gets a proper
 * language code and a readable name ("English (United States) · original"), and the one that plays by
 * default is the original, or else English, or else the first.
 */
object YouTubeAudioTracks {
    private const val ROLE_SCHEME = "urn:mpeg:DASH:role:2011"

    enum class Kind(val label: String) { ORIGINAL("original"), DUBBED("dubbed"), AUTO("auto-dubbed"), DESCRIPTIVE("audio description"), UNKNOWN("") }

    class Track(val code: String, val kind: Kind)

    /** "en-US (original)", "es-US (dubbed-auto)" or "de-DE.10" into a language code and what sort of track it is. */
    fun parse(raw: String): Track {
        val text = raw.trim()
        Regex("""^(.*?)\s*\((.*)\)\s*$""").find(text)?.let { m ->
            val note = m.groupValues[2].lowercase()
            val kind = when {
                "auto" in note -> Kind.AUTO
                "original" in note -> Kind.ORIGINAL
                "descript" in note -> Kind.DESCRIPTIVE
                "dub" in note -> Kind.DUBBED
                else -> Kind.UNKNOWN
            }
            return Track(m.groupValues[1].ifBlank { "und" }, kind)
        }
        // "de-DE.10": the number says which kind (4 is the original, 2 an audio description).
        Regex("""^([A-Za-z]{2,3}(?:-[A-Za-z0-9]+)*)\.(\d+)$""").find(text)?.let { m ->
            val kind = when (m.groupValues[2]) { "4" -> Kind.ORIGINAL; "2" -> Kind.DESCRIPTIVE; else -> Kind.DUBBED }
            return Track(m.groupValues[1], kind)
        }
        return Track(text.ifBlank { "und" }, Kind.UNKNOWN)
    }

    private fun language(code: String): String {
        val locale = Locale.forLanguageTag(code)
        return locale.getDisplayName(Locale.ENGLISH).takeIf { it.isNotBlank() && !it.equals(code, true) } ?: code
    }

    /** The manifest with its sound tracks named and ordered; the manifest as it was if anything goes wrong. */
    fun tidy(manifest: ByteArray): ByteArray = runCatching {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(manifest))
        val period = doc.getElementsByTagName("Period").item(0) ?: return@runCatching manifest
        fun kids(n: Node, name: String) = (0 until n.childNodes.length).map { n.childNodes.item(it) }.filter { it.nodeName == name }
        fun attr(n: Node, a: String) = (n as? Element)?.getAttribute(a).orEmpty()
        val audioSets = kids(period, "AdaptationSet").filter { s ->
            attr(s, "mimeType").startsWith("audio") || kids(s, "Representation").any { attr(it, "mimeType").startsWith("audio") }
        }
        if (audioSets.isEmpty()) return@runCatching manifest
        val parsed = audioSets.associateWith { parse(attr(it, "lang")) }
        // The default: the original; else English; else the first.
        val defaultTrack = parsed.entries.firstOrNull { it.value.kind == Kind.ORIGINAL }?.value
            ?: parsed.entries.firstOrNull { it.value.code.startsWith("en", ignoreCase = true) && it.value.kind != Kind.DESCRIPTIVE && it.value.kind != Kind.AUTO }?.value
            ?: parsed.entries.firstOrNull { it.value.kind != Kind.DESCRIPTIVE }?.value
            ?: parsed.values.first()
        fun isDefault(t: Track) = t.code.equals(defaultTrack.code, ignoreCase = true) && t.kind == defaultTrack.kind
        for ((set, track) in parsed) {
            val element = set as Element
            element.setAttribute("lang", track.code)
            // A readable name, and the roles: "main" is what plays by default; the others are alternatives.
            kids(set, "Label").forEach { set.removeChild(it) }
            kids(set, "Role").forEach { set.removeChild(it) }
            val label = doc.createElement("Label").apply {
                textContent = language(track.code) + (track.kind.label.takeIf { it.isNotBlank() }?.let { " \u00B7 $it" } ?: "")
            }
            val role = doc.createElement("Role").apply {
                setAttribute("schemeIdUri", ROLE_SCHEME)
                setAttribute("value", when {
                    isDefault(track) -> "main"
                    track.kind == Kind.DESCRIPTIVE -> "description"
                    track.kind == Kind.DUBBED || track.kind == Kind.AUTO -> "dub"
                    else -> "alternate"
                })
            }
            set.insertBefore(role, set.firstChild)
            set.insertBefore(label, set.firstChild)
        }
        // The default tracks go first.
        val first = audioSets.firstOrNull { isDefault(parsed.getValue(it)) }
        val firstAudio = audioSets.first()
        if (first != null && first !== firstAudio) period.insertBefore(first, firstAudio)
        audioSets.filter { it !== first && isDefault(parsed.getValue(it)) }.forEach { period.insertBefore(it, firstAudio) }
        val out = StringWriter()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(doc), StreamResult(out))
        out.toString().toByteArray(Charsets.UTF_8)
    }.getOrDefault(manifest)
}
