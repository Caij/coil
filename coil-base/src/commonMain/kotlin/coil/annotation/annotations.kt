package coil.annotation

/**
 * Marks declarations that are still **experimental**.
 *
 * Targets marked by this annotation may contain breaking changes in the future as their design
 * is still incubating.
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class ExperimentalCoilApi

/**
 * Marks declarations that are **internal** in Coil's API.
 *
 * Targets marked by this annotation should not be used outside of Coil because their signatures
 * and semantics will change between future releases without any warnings and without providing
 * any migration aids.
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
annotation class InternalCoilApi

/**
 * Marks declarations that have their visibility relaxed to make code easier to test.
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.SOURCE)
internal annotation class VisibleForTesting

/**
 * Marks declarations that should only be called from the main thread.
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.SOURCE)
internal annotation class MainThread

/**
 * Marks declarations that should only be called from a worker thread (on platforms that have
 * multiple threads).
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.SOURCE)
internal annotation class WorkerThread
