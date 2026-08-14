package com.qonversion.android.sdk

/**
 * Marks a Qonversion API that is still taking shape.
 *
 * A declaration annotated with this marker is shipped so integrators can try it, but it is
 * explicitly **not** a stability promise: its signature, semantics and even its existence may
 * change in any release without a deprecation cycle.
 *
 * Kotlin callers opt in with `@OptIn(ExperimentalQonversionApi::class)`; Java callers can use the
 * API directly, since the opt-in requirement is a Kotlin compiler concept only.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This Qonversion API is experimental. Its behavior and signature may change " +
        "without notice. Opt in with @OptIn(ExperimentalQonversionApi::class).",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
annotation class ExperimentalQonversionApi
