package io.github.chsbuffer.revancedxposed

// Skip Unit Test on unused fingerprint.
// Use with caution!!!
@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.FUNCTION)
annotation class SkipTest()

class AppVersion(val versionString: String) : Comparable<AppVersion> {
    init {
        require(versionString.matches(Regex("\\d+\\.\\d+(\\.\\d+)?(\\.\\d+)?"))) {
            "Version string must be in the format major.minor[.build][.revision] (e.g., 1.2 or 1.2.3)"
        }
    }

    private val parts: List<Int> by lazy { versionString.split('.').map { it.toInt() } }

    val major: Int
        get() = parts[0]

    val minor: Int
        get() = parts[1]

    val build: Int
        get() = parts.elementAtOrElse(2) { 0 }

    val revision: Int
        get() = parts.elementAtOrElse(3) { 0 }

    override fun compareTo(other: AppVersion): Int {
        if (this.major != other.major) {
            return this.major.compareTo(other.major)
        }
        if (this.minor != other.minor) {
            return this.minor.compareTo(other.minor)
        }
        if (this.build != other.build) {
            return this.build.compareTo(other.build)
        }
        return this.revision.compareTo(other.revision)
    }

    override fun toString(): String = versionString
}

// Skip Unit Test by version constraint
@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.FUNCTION)
annotation class RequireAppVersion(
    val minVersion: String = "",
    val maxVersion: String = ""
)

class VersionConstraintFailedException(message: String) : Exception(message)

fun match(appVersion: AppVersion, minVersionStr: String, maxVersionStr: String) {
    val minVersion = minVersionStr.takeIf { it.isNotEmpty() }?.let { AppVersion(it) }
    val maxVersion = maxVersionStr.takeIf { it.isNotEmpty() }?.let { AppVersion(it) }
    when {
        minVersion == null && maxVersion == null -> return // No version constraint
        minVersion != null && appVersion < minVersion ->
            throw VersionConstraintFailedException("Min version mismatch (current: $appVersion, required: $minVersion)")

        maxVersion != null && appVersion > maxVersion ->
            throw VersionConstraintFailedException("Max version mismatch (current: $appVersion, required: $maxVersion)")
    }
}
