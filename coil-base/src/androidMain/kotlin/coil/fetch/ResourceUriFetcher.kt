package coil.fetch

import android.content.ContentResolver.SCHEME_ANDROID_RESOURCE
import android.util.TypedValue
import coil.ImageLoader
import coil.Uri
import coil.asCoilImage
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.decode.ResourceMetadata
import coil.pathSegments
import coil.request.Options
import coil.request.bitmapConfig
import coil.util.DrawableUtils
import coil.util.MIME_TYPE_XML
import coil.util.MimeTypeMap
import coil.util.getDrawableCompat
import coil.util.getXmlDrawableCompat
import coil.util.isVector
import coil.util.toDrawable
import okio.buffer
import okio.source

internal class ResourceUriFetcher(
    private val data: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // Expected format: android.resource://example.package.name/12345678
        val packageName =
            data.authority?.takeIf { it.isNotBlank() } ?: throwInvalidUriException(data)
        val resId = data.pathSegments.lastOrNull()?.toIntOrNull() ?: throwInvalidUriException(data)

        val context = options.context
        val resources = if (packageName == context.packageName) {
            context.resources
        } else {
            context.packageManager.getResourcesForApplication(packageName)
        }
        val path = TypedValue().apply { resources.getValue(resId, this, true) }.string
        val entryName = path.substring(path.lastIndexOf('/'))
        val mimeType = MimeTypeMap.getMimeTypeFromUrl(entryName)

        return if (mimeType == MIME_TYPE_XML) {
            // getDrawableCompat can only load resources that are in the current package.
            val drawable = if (packageName == context.packageName) {
                context.getDrawableCompat(resId)
            } else {
                context.getXmlDrawableCompat(resources, resId)
            }

            val isVector = drawable.isVector
            ImageFetchResult(
                image = if (isVector) {
                    DrawableUtils.convertToBitmap(
                        drawable = drawable,
                        config = options.bitmapConfig,
                        size = options.size,
                        scale = options.scale,
                        allowInexactSize = options.allowInexactSize,
                    ).toDrawable(context)
                } else {
                    drawable
                }.asCoilImage(),
                isSampled = isVector,
                dataSource = DataSource.DISK,
            )
        } else {
            val typedValue = TypedValue()
            val inputStream = resources.openRawResource(resId, typedValue)
            SourceFetchResult(
                source = ImageSource(
                    source = inputStream.source().buffer(),
                    fileSystem = options.fileSystem,
                    metadata = ResourceMetadata(packageName, resId, typedValue.density),
                ),
                mimeType = mimeType,
                dataSource = DataSource.DISK,
            )
        }
    }

    private fun throwInvalidUriException(data: Uri): Nothing {
        throw IllegalStateException("Invalid $SCHEME_ANDROID_RESOURCE URI: $data")
    }

    class Factory : Fetcher.Factory<Uri> {

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!isApplicable(data)) return null
            return ResourceUriFetcher(data, options)
        }

        private fun isApplicable(data: Uri): Boolean {
            return data.scheme == SCHEME_ANDROID_RESOURCE
        }
    }
}
