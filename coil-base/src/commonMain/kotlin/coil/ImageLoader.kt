package coil

import android.content.Context
import androidx.lifecycle.Lifecycle
import coil.decode.Decoder
import coil.disk.DiskCache
import coil.disk.singletonDiskCache
import coil.drawable.CrossfadeDrawable
import coil.fetch.Fetcher
import coil.intercept.Interceptor
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.DefaultRequestOptions
import coil.request.Disposable
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.Precision
import coil.target.ViewTarget
import coil.transition.CrossfadeTransition
import coil.transition.Transition
import coil.util.DEFAULT_CROSSFADE_MILLIS
import coil.util.DEFAULT_REQUEST_OPTIONS
import coil.util.Logger
import io.ktor.client.HttpClient
import kotlin.jvm.JvmSynthetic
import kotlinx.coroutines.CoroutineDispatcher

/**
 * A service class that loads images by executing [ImageRequest]s. Image loaders handle
 * caching, data fetching, image decoding, request management, memory management, and more.
 *
 * Image loaders are designed to be shareable and work best when you create a single
 * instance and share it throughout your app.
 */
interface ImageLoader {

    /**
     * The default options that are used to fill in unset [ImageRequest] values.
     */
    val defaults: DefaultRequestOptions

    /**
     * The components used to fulfil image requests.
     */
    val components: ComponentRegistry

    /**
     * An in-memory cache of previously loaded images.
     */
    val memoryCache: MemoryCache?

    /**
     * An on-disk cache of previously loaded images.
     */
    val diskCache: DiskCache?

    /**
     * Enqueue the [request] to be executed asynchronously.
     *
     * NOTE: The request will wait until [ImageRequest.lifecycle] is at least
     * [Lifecycle.State.STARTED] before being executed.
     *
     * @param request The request to execute.
     * @return A [Disposable] which can be used to cancel or check the status of the request.
     */
    fun enqueue(request: ImageRequest): Disposable

    /**
     * Execute the [request] in the current coroutine scope.
     *
     * NOTE: If [ImageRequest.target] is a [ViewTarget], the job will automatically be cancelled
     * if its view is detached.
     *
     * @param request The request to execute.
     * @return A [SuccessResult] if the request completes successfully.
     *  Else, returns an [ErrorResult].
     */
    suspend fun execute(request: ImageRequest): ImageResult

    /**
     * Cancel any new and in progress requests, clear the [MemoryCache], and close any open
     * system resources.
     *
     * Shutting down an image loader is optional. It will be shut down automatically if
     * dereferenced.
     */
    fun shutdown()

    /**
     * Create an [ImageLoader.Builder] that shares the same resources and configuration as this
     * image loader.
     */
    fun newBuilder(): Builder

    class Builder {

        private val applicationContext: PlatformContext
        private var defaults: DefaultRequestOptions
        private var memoryCacheLazy: Lazy<MemoryCache?>?
        private var diskCacheLazy: Lazy<DiskCache?>?
        private var httpClientLazy: Lazy<HttpClient>?
        private var eventListenerFactory: EventListener.Factory?
        private var componentRegistry: ComponentRegistry?
        private var logger: Logger?
        private val extras = Extras.Builder()

        constructor(context: PlatformContext) {
            applicationContext = context.application
            defaults = DEFAULT_REQUEST_OPTIONS
            memoryCacheLazy = null
            diskCacheLazy = null
            httpClientLazy = null
            eventListenerFactory = null
            componentRegistry = null
            logger = null
        }

        internal constructor(options: RealImageLoader.Options) {
            applicationContext = options.applicationContext
            defaults = options.defaults
            memoryCacheLazy = options.memoryCacheLazy
            diskCacheLazy = options.diskCacheLazy
            httpClientLazy = options.httpClientLazy
            eventListenerFactory = options.eventListenerFactory
            componentRegistry = options.componentRegistry
            logger = options.logger
        }

        /**
         * Set the [HttpClient] used for network requests.
         */
        fun httpClient(httpClient: HttpClient) = apply {
            this.httpClientLazy = lazyOf(httpClient)
        }

        /**
         * Set a lazy callback to create the [HttpClient] used for network requests.
         *
         * This allows lazy creation of the [HttpClient] on a background thread.
         * [initializer] is guaranteed to be called at most once.
         *
         * Prefer using this instead of `httpClient(HttpClient)`.
         */
        fun httpClient(initializer: () -> HttpClient) = apply {
            this.httpClientLazy = lazy(initializer)
        }

        /**
         * Build and set the [ComponentRegistry].
         */
        @JvmSynthetic
        inline fun components(
            builder: ComponentRegistry.Builder.() -> Unit,
        ) = components(ComponentRegistry.Builder().apply(builder).build())

        /**
         * Set the [ComponentRegistry].
         */
        fun components(components: ComponentRegistry) = apply {
            this.componentRegistry = components
        }

        /**
         * Set the [MemoryCache].
         */
        fun memoryCache(memoryCache: MemoryCache?) = apply {
            this.memoryCacheLazy = lazyOf(memoryCache)
        }

        /**
         * Set a lazy callback to create the [MemoryCache].
         *
         * Prefer using this instead of `memoryCache(MemoryCache)`.
         */
        fun memoryCache(initializer: () -> MemoryCache?) = apply {
            this.memoryCacheLazy = lazy(initializer)
        }

        /**
         * Set the [DiskCache].
         *
         * NOTE: By default, [ImageLoader]s share the same disk cache instance. This is necessary
         * as having multiple disk cache instances active in the same directory at the same time
         * can corrupt the disk cache.
         *
         * @see DiskCache.directory
         */
        fun diskCache(diskCache: DiskCache?) = apply {
            this.diskCacheLazy = lazyOf(diskCache)
        }

        /**
         * Set a lazy callback to create the [DiskCache].
         *
         * Prefer using this instead of `diskCache(DiskCache)`.
         *
         * NOTE: By default, [ImageLoader]s share the same disk cache instance. This is necessary
         * as having multiple disk cache instances active in the same directory at the same time
         * can corrupt the disk cache.
         *
         * @see DiskCache.directory
         */
        fun diskCache(initializer: () -> DiskCache?) = apply {
            this.diskCacheLazy = lazy(initializer)
        }

        /**
         * Set a single [EventListener] that will receive all callbacks for requests launched by
         * this image loader.
         *
         * @see eventListenerFactory
         */
        fun eventListener(listener: EventListener) = eventListenerFactory { listener }

        /**
         * Set the [EventListener.Factory] to create per-request [EventListener]s.
         */
        fun eventListenerFactory(factory: EventListener.Factory) = apply {
            this.eventListenerFactory = factory
        }

        /**
         * Enable a crossfade animation with duration [CrossfadeDrawable.DEFAULT_DURATION]
         * milliseconds when a request completes successfully.
         *
         * Default: false
         */
        fun crossfade(
            enable: Boolean,
        ) = crossfade(if (enable) DEFAULT_CROSSFADE_MILLIS else 0)

        /**
         * Enable a crossfade animation with [durationMillis] milliseconds when a request completes
         * successfully.
         *
         * @see `crossfade(Boolean)`
         */
        fun crossfade(durationMillis: Int) = apply {
            val factory = if (durationMillis > 0) {
                CrossfadeTransition.Factory(durationMillis)
            } else {
                Transition.Factory.NONE
            }
            transitionFactory(factory)
        }

        /**
         * Set the default [Transition.Factory] for each request.
         */
        fun transitionFactory(factory: Transition.Factory) = apply {
            this.defaults = this.defaults.copy(transitionFactory = factory)
        }

        /**
         * Set the default precision for a request. [Precision] controls whether the size of the
         * loaded image must match the request's size exactly or not.
         *
         * Default: [Precision.AUTOMATIC]
         */
        fun precision(precision: Precision) = apply {
            this.defaults = this.defaults.copy(precision = precision)
        }

        /**
         * A convenience function to set [fetcherDispatcher], [decoderDispatcher], and
         * [transformationDispatcher] in one call.
         */
        fun dispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.defaults = this.defaults.copy(
                fetcherDispatcher = dispatcher,
                decoderDispatcher = dispatcher,
                transformationDispatcher = dispatcher,
            )
        }

        /**
         * The [CoroutineDispatcher] that the [Interceptor] chain will be executed on.
         *
         * Default: `Dispatchers.Main.immediate`
         */
        fun interceptorDispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.defaults = this.defaults.copy(interceptorDispatcher = dispatcher)
        }

        /**
         * The [CoroutineDispatcher] that [Fetcher.fetch] will be executed on.
         *
         * Default: `Dispatchers.IO`
         */
        fun fetcherDispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.defaults = this.defaults.copy(fetcherDispatcher = dispatcher)
        }

        /**
         * The [CoroutineDispatcher] that [Decoder.decode] will be executed on.
         *
         * Default: `Dispatchers.IO`
         */
        fun decoderDispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.defaults = this.defaults.copy(decoderDispatcher = dispatcher)
        }

        /**
         * Set the default placeholder image to use when a request starts.
         */
        fun placeholder(image: Image?) = apply {
            this.defaults = this.defaults.copy(placeholder = image)
        }

        /**
         * Set the default error image to use when a request fails.
         */
        fun error(image: Image?) = apply {
            this.defaults = this.defaults.copy(error = image)
        }

        /**
         * Set the default fallback image to use if [ImageRequest.data] is null.
         */
        fun fallback(image: Image?) = apply {
            this.defaults = this.defaults.copy(fallback = image)
        }

        /**
         * Set the default memory cache policy.
         */
        fun memoryCachePolicy(policy: CachePolicy) = apply {
            this.defaults = this.defaults.copy(memoryCachePolicy = policy)
        }

        /**
         * Set the default disk cache policy.
         */
        fun diskCachePolicy(policy: CachePolicy) = apply {
            this.defaults = this.defaults.copy(diskCachePolicy = policy)
        }

        /**
         * Set the default network cache policy.
         *
         * NOTE: Disabling writes has no effect.
         */
        fun networkCachePolicy(policy: CachePolicy) = apply {
            this.defaults = this.defaults.copy(networkCachePolicy = policy)
        }

        /**
         * Set the [Logger] to write logs to.
         *
         * NOTE: Setting a [Logger] can reduce performance and should be avoided in release builds.
         */
        fun logger(logger: Logger?) = apply {
            this.logger = logger
        }

        /**
         * Set an extra for extension.
         */
        fun extra(key: String, value: Any?) = apply {
            extras.put(key, value)
        }

        /**
         * Create a new [ImageLoader] instance.
         */
        fun build(): ImageLoader {
            val options = RealImageLoader.Options(
                applicationContext = applicationContext,
                defaults = defaults,
                memoryCacheLazy = memoryCacheLazy ?: lazy { MemoryCache.Builder(applicationContext).build() },
                diskCacheLazy = diskCacheLazy ?: lazy { singletonDiskCache() },
                httpClientLazy = httpClientLazy ?: lazy { HttpClient() },
                eventListenerFactory = eventListenerFactory ?: EventListener.Factory.NONE,
                componentRegistry = componentRegistry ?: ComponentRegistry(),
                logger = logger,
                extras = extras.build(),
            )
            return RealImageLoader(options)
        }
    }
}
