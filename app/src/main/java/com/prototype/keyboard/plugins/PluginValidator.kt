package com.prototype.keyboard.plugins

/**
 * Manifest policy checks. Returns a list of human-readable errors;
 * empty means valid. Pure Kotlin, unit-tested (PluginValidatorTest).
 */
object PluginValidator {

    private val ID_REGEX = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
    private val VERSION_REGEX = Regex("^\\d+\\.\\d+\\.\\d+$")

    fun validateManifest(m: PluginManifest): List<String> {
        val errors = ArrayList<String>()
        if (!ID_REGEX.matches(m.id)) errors.add("bad id '${m.id}' (use reverse-dns, a-z 0-9 . _ -)")
        if (m.name.isBlank() || m.name.length > 48) errors.add("name must be 1..48 chars")
        if (m.author.isBlank() || m.author.length > 48) errors.add("author must be 1..48 chars")
        if (!VERSION_REGEX.matches(m.version)) errors.add("version must look like 1.0.0")
        if (m.description.length > 280) errors.add("description too long (max 280)")
        if (m.pluginApiVersion !in PluginApi.SUPPORTED_API) {
            errors.add("needs plugin API v${m.pluginApiVersion} (host supports ${PluginApi.SUPPORTED_API})")
        }
        if (!VERSION_REGEX.matches(m.minHostVersion)) {
            errors.add("minHostVersion must look like 0.3.0")
        } else if (PluginApi.compareSemver(PluginApi.HOST_VERSION, m.minHostVersion) < 0) {
            errors.add("needs host ${m.minHostVersion}+ (this host is ${PluginApi.HOST_VERSION})")
        }
        return errors
    }
}
