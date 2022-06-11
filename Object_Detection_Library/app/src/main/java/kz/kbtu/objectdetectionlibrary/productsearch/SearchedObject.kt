package kz.kbtu.objectdetectionlibrary.productsearch

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import kz.kbtu.objectdetectionlibrary.R
import kz.kbtu.objectdetectionlibrary.objectDetection.DetectedObjectInfo
import kz.kbtu.objectdetectionlibrary.utils.Utils

/** Hosts the detected object info and its search result.  */
class SearchedObject(
    resources: Resources,
    private val detectedObject: DetectedObjectInfo,
    val productList: List<Product>
) {

    private val objectThumbnailCornerRadius: Int =
        resources.getDimensionPixelOffset(R.dimen.bounding_box_corner_radius)
    private var objectThumbnail: Bitmap? = null

    val objectIndex: Int
        get() = detectedObject.objectIndex

    val boundingBox: Rect
        get() = detectedObject.boundingBox

    @Synchronized
    fun getObjectThumbnail(): Bitmap = objectThumbnail ?: let {
        Utils.getCornerRoundedBitmap(detectedObject.getBitmap(), objectThumbnailCornerRadius)
            .also { objectThumbnail = it }
    }
}
