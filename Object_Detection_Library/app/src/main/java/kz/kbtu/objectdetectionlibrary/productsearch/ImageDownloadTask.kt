package kz.kbtu.objectdetectionlibrary.productsearch


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.text.TextUtils
import android.util.Log
import android.widget.ImageView
import java.net.URL

/** todo: migrate to Coroutines. */
/** Async task to download the image and then feed into the provided image view.  */
internal class ImageDownloadTask(private val imageView: ImageView, private val maxImageWidth: Int) :
    AsyncTask<String, Void, Bitmap>() {

    override fun doInBackground(vararg urls: String): Bitmap? {
        if (TextUtils.isEmpty(urls[0])) {
            return null
        }

        var bitmap: Bitmap? = null
        try {
            val inputStream = URL(urls[0]).openStream()
            bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "Image download failed: ${urls[0]}")
        }

        if (bitmap != null && bitmap.width > maxImageWidth) {
            val dstHeight = (maxImageWidth.toFloat() / bitmap.width * bitmap.height).toInt()
            bitmap = Bitmap.createScaledBitmap(bitmap, maxImageWidth, dstHeight, /* filter= */ false)
        }
        return bitmap
    }

    override fun onPostExecute(result: Bitmap?) {
        result?.let {
            imageView.setImageBitmap(result)
        }
    }

    companion object {
        private const val TAG = "ImageDownloadTask"
    }
}
