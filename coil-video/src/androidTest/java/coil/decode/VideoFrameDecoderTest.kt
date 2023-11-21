package coil.decode

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import androidx.test.core.app.ApplicationProvider
import coil.Extras
import coil.FileMediaDataSource
import coil.fetch.MediaDataSourceFetcher
import coil.request.Options
import coil.request.videoFrameMicros
import coil.request.videoFramePercent
import coil.size.Size
import coil.util.assertIsSimilarTo
import coil.util.assumeTrue
import coil.util.bitmap
import coil.util.copyAssetToFile
import coil.util.decodeBitmapAsset
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.buffer
import okio.source
import org.junit.Before
import org.junit.Test

class VideoFrameDecoderTest {

    private lateinit var context: Context

    @Before
    fun before() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun noSetFrameTime() = runTest(timeout = 1.minutes) {
        // MediaMetadataRetriever does not work on the emulator pre-API 23.
        assumeTrue(SDK_INT >= 23)

        val result = VideoFrameDecoder(
            source = ImageSource(
                source = context.assets.open("video.mp4").source().buffer(),
                fileSystem = FileSystem.SYSTEM,
            ),
            options = Options(context)
        ).decode()

        val actual = result.image.bitmap
        assertNotNull(actual)
        assertFalse(result.isSampled)

        val expected = context.decodeBitmapAsset("video_frame_1.jpg")
        actual.assertIsSimilarTo(expected)
    }

    @Test
    fun specificFrameTime() = runTest(timeout = 1.minutes) {
        // MediaMetadataRetriever does not work on the emulator pre-API 23.
        assumeTrue(SDK_INT >= 23)

        val result = VideoFrameDecoder(
            source = ImageSource(
                source = context.assets.open("video.mp4").source().buffer(),
                fileSystem = FileSystem.SYSTEM,
            ),
            options = Options(
                context = context,
                extras = Extras.Builder()
                    .set(Extras.Key.videoFrameMicros, 32600000L)
                    .build()
            )
        ).decode()

        val actual = result.image.bitmap
        assertNotNull(actual)
        assertFalse(result.isSampled)

        val expected = context.decodeBitmapAsset("video_frame_2.jpg")
        actual.assertIsSimilarTo(expected)
    }

    @Test
    fun specificFramePercent() = runTest(timeout = 1.minutes) {
        // MediaMetadataRetriever does not work on the emulator pre-API 23.
        assumeTrue(SDK_INT >= 23)

        val result = VideoFrameDecoder(
            source = ImageSource(
                source = context.assets.open("video.mp4").source().buffer(),
                fileSystem = FileSystem.SYSTEM,
            ),
            options = Options(
                context = context,
                extras = Extras.Builder()
                    .set(Extras.Key.videoFramePercent, 0.525)
                    .build(),
            )
        ).decode()

        val actual = result.image.bitmap
        assertNotNull(actual)
        assertFalse(result.isSampled)

        val expected = context.decodeBitmapAsset("video_frame_2.jpg")
        actual.assertIsSimilarTo(expected)
    }

    @Test
    fun rotation() = runTest(timeout = 1.minutes) {
        // MediaMetadataRetriever does not work on the emulator pre-API 23.
        assumeTrue(SDK_INT >= 23)

        val result = VideoFrameDecoder(
            source = ImageSource(
                source = context.assets.open("video_rotated.mp4").source().buffer(),
                fileSystem = FileSystem.SYSTEM,
            ),
            options = Options(context, size = Size(150, 150))
        ).decode()

        val actual = result.image.bitmap
        assertNotNull(actual)
        assertTrue(result.isSampled)

        val expected = context.decodeBitmapAsset("video_frame_rotated.jpg")
        actual.assertIsSimilarTo(expected, threshold = 0.97)
    }

    /** Regression test: https://github.com/coil-kt/coil/issues/1482 */
    @Test
    fun nestedAsset() = runTest(timeout = 1.minutes) {
        // MediaMetadataRetriever does not work on the emulator pre-API 23.
        assumeTrue(SDK_INT >= 23)

        val path = "nested/video.mp4"
        val result = VideoFrameDecoder(
            source = ImageSource(
                source = context.assets.open(path).source().buffer(),
                fileSystem = FileSystem.SYSTEM,
                metadata = AssetMetadata(path),
            ),
            options = Options(context)
        ).decode()

        val actual = result.image.bitmap
        assertNotNull(actual)
        assertFalse(result.isSampled)

        val expected = context.decodeBitmapAsset("video_frame_1.jpg")
        actual.assertIsSimilarTo(expected)
    }

    @Test
    fun mediaDataSource() = runTest(timeout = 1.minutes) {
        // MediaMetadataRetriever does not work on the emulator pre-API 23.
        assumeTrue(SDK_INT >= 23)
        val file = context.copyAssetToFile("video.mp4")

        val dataSource = FileMediaDataSource(file)
        val result = VideoFrameDecoder(
            source = ImageSource(
                source = MediaDataSourceFetcher.MediaDataSourceOkioSource(dataSource).buffer(),
                fileSystem = FileSystem.SYSTEM,
                metadata = MediaDataSourceFetcher.MediaSourceMetadata(dataSource),
            ),
            options = Options(context),
        ).decode()

        val actual = result.image.bitmap
        assertNotNull(actual)
        assertFalse(result.isSampled)

        val expected = context.decodeBitmapAsset("video_frame_1.jpg")
        actual.assertIsSimilarTo(expected)
    }
}
