package kz.kbtu.objectdetectionlibrary.objectDetection

import android.animation.ValueAnimator
import android.graphics.RectF
import android.util.Log
import androidx.annotation.MainThread
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kz.kbtu.objectdetectionlibrary.R
import kz.kbtu.objectdetectionlibrary.camera.*
import kz.kbtu.objectdetectionlibrary.utils.InputInfo
import kz.kbtu.objectdetectionlibrary.utils.PreferenceUtils
import java.io.IOException

/** A processor to run object detector in prominent object only mode.  */
class ProminentObjectProcessor(
    graphicOverlay: GraphicOverlay,
    private val workflowModel: WorkflowModel,
    private val customModelPath: String? = null
) :
    FrameProcessorBase<List<DetectedObject>>() {

    private val detector: ObjectDetector
    private val confirmationController: ObjectConfirmationController =
        ObjectConfirmationController(graphicOverlay)
    private val cameraReticleAnimator: CameraReticleAnimator = CameraReticleAnimator(graphicOverlay)
    private val reticleOuterRingRadius: Int = graphicOverlay
        .resources
        .getDimensionPixelOffset(R.dimen.object_reticle_outer_ring_stroke_radius)

    init {
        val options: ObjectDetectorOptionsBase
        val isClassificationEnabled =
            PreferenceUtils.isClassificationEnabled(graphicOverlay.context)
        if (customModelPath != null) {
            val localModel = LocalModel.Builder()
                .setAssetFilePath(customModelPath)
                .build()
            options = CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .build()
        } else {
            val optionsBuilder = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            if (isClassificationEnabled) {
                optionsBuilder.enableClassification()
            }
            options = optionsBuilder.build()
        }

        this.detector = ObjectDetection.getClient(options)
    }

    override fun stop() {
        super.stop()
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close object detector!", e)
        }
    }

    override fun detectInImage(image: InputImage): Task<List<DetectedObject>> {
        return detector.process(image)
    }

    @MainThread
    override fun onSuccess(
        inputInfo: InputInfo,
        results: List<DetectedObject>,
        graphicOverlay: GraphicOverlay
    ) {
        var objects = results
        if (!workflowModel.isCameraLive) {
            return
        }

        if (PreferenceUtils.isClassificationEnabled(graphicOverlay.context)) {
            val qualifiedObjects = ArrayList<DetectedObject>()
            qualifiedObjects.addAll(objects)
            objects = qualifiedObjects
        }

        val objectIndex = 0
        val hasValidObjects = objects.isNotEmpty() &&
                (customModelPath == null || DetectedObjectInfo.hasValidLabels(objects[objectIndex]))
        if (!hasValidObjects) {
            confirmationController.reset()
            workflowModel.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
        } else {
            val visionObject = objects[objectIndex]
            if (objectBoxOverlapsConfirmationReticle(
                    graphicOverlay,
                    visionObject
                ) && detectedValidObjects(visionObject.labels)
            ) {
                // User is confirming the object selection.
                Log.e("ARAY_ABOVE", "object is detected and confirmed")
                confirmationController.confirming(visionObject.trackingId)
                workflowModel.confirmingObject(
                    DetectedObjectInfo(visionObject, objectIndex, inputInfo),
                    confirmationController.progress
                )
            } else {
                // Object detected but user doesn't want to pick this one.
                Log.e("ARAY_ABOVE", "object is detected but not confirmed")
                confirmationController.reset()
                workflowModel.setWorkflowState(WorkflowModel.WorkflowState.DETECTED)
            }
        }

        graphicOverlay.clear()
        if (!hasValidObjects) {
            graphicOverlay.add(ObjectReticleGraphic(graphicOverlay, cameraReticleAnimator))
            cameraReticleAnimator.start()
        } else {
            if (objectBoxOverlapsConfirmationReticle(
                    graphicOverlay,
                    objects[0]
                ) && detectedValidObjects(objects[0].labels)
            ) {
                // User is confirming the object selection.
                val barcodeGraphic =
                    Log.e("ARAY", "object is detected and confirmed")
                cameraReticleAnimator.cancel()
                graphicOverlay.add(
                    ObjectReticleGraphic(
                        graphicOverlay,
                        cameraReticleAnimator,
                        true
                    )
                )

//                val loadingAnimator = createLoadingAnimator(graphicOverlay)
//                loadingAnimator.start()
//                graphicOverlay.add(BarcodeLoadingGraphic(graphicOverlay, loadingAnimator))
//                graphicOverlay.add(
//                        ObjectGraphicInProminentMode(
//                                graphicOverlay, objects[0], confirmationController
//                        )
//                )

            } else {
                // Object is detected but the confirmation reticle is moved off the object box, which
                // indicates user is not trying to pick this object.
                Log.e("ARAY", "object is detected but not confirmed")
                graphicOverlay.add(ObjectReticleGraphic(graphicOverlay, cameraReticleAnimator))
                cameraReticleAnimator.start()
            }
        }
        graphicOverlay.invalidate()
    }

    private fun objectBoxOverlapsConfirmationReticle(
        graphicOverlay: GraphicOverlay,
        visionObject: DetectedObject
    ): Boolean {
        val boxRect = graphicOverlay.translateRect(visionObject.boundingBox)
        val reticleCenterX = graphicOverlay.width / 2f
        val reticleCenterY = graphicOverlay.height / 2f
        val reticleRect = RectF(
            reticleCenterX - reticleOuterRingRadius,
            reticleCenterY - reticleOuterRingRadius,
            reticleCenterX + reticleOuterRingRadius,
            reticleCenterY + reticleOuterRingRadius
        )
        return reticleRect.intersect(boxRect)
    }

    private fun createLoadingAnimator(graphicOverlay: GraphicOverlay): ValueAnimator {
        val endProgress = 1.1f
        return ValueAnimator.ofFloat(0f, endProgress).apply {
            duration = 2000
            addUpdateListener {
                if ((animatedValue as Float).compareTo(endProgress) >= 0) {
                    graphicOverlay.clear()
                } else {
                    graphicOverlay.invalidate()
                }
            }
        }
    }

    private fun detectedValidObjects(labels: List<DetectedObject.Label>) =
        labels.find { it.text == "Driver's license" } != null

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Object detection failed!", e)
    }

    companion object {
        private const val TAG = "ProminentObjProcessor"
    }
}
