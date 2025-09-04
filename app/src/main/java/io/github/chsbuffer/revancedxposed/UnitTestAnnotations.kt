package io.github.chsbuffer.revancedxposed

// Skip Unit Test on unused fingerprint.
// Use with caution!!!
@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.FUNCTION)
annotation class SkipTest()
