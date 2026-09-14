package com.prototype.keyboard.plugins

/**
 * Plugin platform contracts (API v1).
 *
 * Compatibility rule: a pack loads iff its [PluginManifest.pluginApiVersion]
 * is in [SUPPORTED_API] AND the host version >= its [PluginManifest.minHostVersion].
 * Hosts never break a supported API version (see docs/PLUGIN_API.md).
 */
object PluginApi {
    const val PLUGIN_API_VERSION = 1
    const val HOST_VERSION = "0.3.0"
    val SUPPORTED_API = 1..1

    fun isCompatible(manifest: PluginManifest): Boolean =
        manifest.pluginApiVersion in SUPPORTED_API &&
            compareSemver(HOST_VERSION, manifest.minHostVersion) >= 0

    /** Three-part semver compare: negative / 0 / positive. Lenient about junk. */
    fun compareSemver(a: String, b: String): Int {
        fun parts(s: String): List<Int> =
            s.split(".").take(3).map { it.filter(Char::isDigit).toIntOrNull() ?: 0 } +
                listOf(0, 0, 0)

        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until 3) {
            if (pa[i] != pb[i]) return pa[i].compareTo(pb[i])
        }
        return 0
    }
}

enum class PackKind { THEME, DICTIONARY, PROMPTS, TOOLS }

data class PluginManifest(
    /** Reverse-dns id, e.g. "com.you.neon". Unique per device. */
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val pluginApiVersion: Int,
    val minHostVersion: String,
    val kind: PackKind,
    val description: String = "",
)
