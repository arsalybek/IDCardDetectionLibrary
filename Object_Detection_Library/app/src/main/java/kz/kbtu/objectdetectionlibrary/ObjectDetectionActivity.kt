package kz.kbtu.objectdetectionlibrary

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.common.base.Objects
import kz.kbtu.objectdetectionlibrary.camera.*
import kz.kbtu.objectdetectionlibrary.camera.WorkflowModel.WorkflowState
import kz.kbtu.objectdetectionlibrary.objectDetection.ProminentObjectProcessor
import kz.kbtu.objectdetectionlibrary.productsearch.BottomSheetScrimView
import kz.kbtu.objectdetectionlibrary.productsearch.Product
import java.io.ByteArrayOutputStream
import java.io.IOException


class ObjectDetectionActivity : AppCompatActivity(), OnClickListener {
    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var settingsButton: View? = null
    private var flashButton: View? = null
    private var promptChip: Chip? = null
    private var promptChipAnimator: AnimatorSet? = null
    private var takePhotoButton: ExtendedFloatingActionButton? = null
    private var searchButtonAnimator: AnimatorSet? = null
    private var workflowModel: WorkflowModel? = null
    private var currentWorkflowState: WorkflowState? = null

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private var bottomSheetScrimView: BottomSheetScrimView? = null
    private var productRecyclerView: RecyclerView? = null
    private var bottomSheetTitleView: TextView? = null
    private var objectThumbnailForBottomSheet: Bitmap? = null
    private var slidingSheetUpFromHiddenState: Boolean = false

    private var isFirstPhotoTaken = false;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_live_object)
        preview = findViewById(R.id.camera_preview)
        graphicOverlay = findViewById<GraphicOverlay>(R.id.camera_preview_graphic_overlay).apply {
            setOnClickListener(this@ObjectDetectionActivity)
            cameraSource = CameraSource(this)
        }
        promptChip = findViewById(R.id.bottom_prompt_chip)
        promptChipAnimator =
            (AnimatorInflater.loadAnimator(this, R.animator.bottom_prompt_chip_enter) as AnimatorSet).apply {
                setTarget(promptChip)
            }
        takePhotoButton = findViewById<ExtendedFloatingActionButton>(R.id.take_photo_button).apply {
            setOnClickListener(this@ObjectDetectionActivity)
        }
        searchButtonAnimator =
            (AnimatorInflater.loadAnimator(this, R.animator.search_button_enter) as AnimatorSet).apply {
                setTarget(takePhotoButton)
            }
        setUpBottomSheet()
        findViewById<View>(R.id.close_button).setOnClickListener(this)
        flashButton = findViewById<View>(R.id.flash_button).apply {
            setOnClickListener(this@ObjectDetectionActivity)
        }
        settingsButton = findViewById<View>(R.id.settings_button).apply {
            setOnClickListener(this@ObjectDetectionActivity)
        }
        setUpWorkflowModel()
    }

    override fun onResume() {
        super.onResume()

        workflowModel?.markCameraFrozen()
        settingsButton?.isEnabled = true
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        currentWorkflowState = WorkflowState.NOT_STARTED
        cameraSource?.setFrameProcessor(
            ProminentObjectProcessor(
                graphicOverlay!!, workflowModel!!,
                CUSTOM_MODEL_PATH
            )
        )
        workflowModel?.setWorkflowState(WorkflowState.DETECTING)
    }

    override fun onPause() {
        super.onPause()
        currentWorkflowState = WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
        cameraSource = null
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            super.onBackPressed()
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.take_photo_button -> {
                //workflowModel?.onSearchButtonClicked()
                stopCameraPreview()
                workflowModel?.setWorkflowState(WorkflowState.CONFIRMED)
            }
            R.id.bottom_sheet_scrim_view -> bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_HIDDEN)
            R.id.close_button -> onBackPressed()
            R.id.flash_button -> {
                if (flashButton?.isSelected == true) {
                    flashButton?.isSelected = false
                    cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_OFF)
                } else {
                    flashButton?.isSelected = true
                    cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
                }
            }
        }
    }

    private fun startCameraPreview() {
        val cameraSource = this.cameraSource ?: return
        val workflowModel = this.workflowModel ?: return
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                preview?.start(cameraSource)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start camera preview!", e)
                cameraSource.release()
                this.cameraSource = null
            }
        }
    }

    private fun stopCameraPreview() {
        if (workflowModel?.isCameraLive == true) {
            workflowModel!!.markCameraFrozen()
            flashButton?.isSelected = false
            preview?.stop()
        }
    }

    private fun setUpBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet))
        bottomSheetBehavior?.setBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    Log.d(TAG, "Bottom sheet new state: $newState")
                    bottomSheetScrimView?.visibility =
                        if (newState == BottomSheetBehavior.STATE_HIDDEN) View.GONE else View.VISIBLE
                    graphicOverlay?.clear()

                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> workflowModel?.setWorkflowState(WorkflowState.DETECTING)
                        BottomSheetBehavior.STATE_COLLAPSED,
                        BottomSheetBehavior.STATE_EXPANDED,
                        BottomSheetBehavior.STATE_HALF_EXPANDED -> slidingSheetUpFromHiddenState = false
                        BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    val searchedObject = workflowModel!!.searchedObject.value
                    if (searchedObject == null || java.lang.Float.isNaN(slideOffset)) {
                        return
                    }

                    val graphicOverlay = graphicOverlay ?: return
                    val bottomSheetBehavior = bottomSheetBehavior ?: return
                    val collapsedStateHeight = bottomSheetBehavior.peekHeight.coerceAtMost(bottomSheet.height)
                    val bottomBitmap = objectThumbnailForBottomSheet ?: return
                    if (slidingSheetUpFromHiddenState) {
                        val thumbnailSrcRect = graphicOverlay.translateRect(searchedObject.boundingBox)
                        bottomSheetScrimView?.updateWithThumbnailTranslateAndScale(
                            bottomBitmap,
                            collapsedStateHeight,
                            slideOffset,
                            thumbnailSrcRect
                        )
                    } else {
                        bottomSheetScrimView?.updateWithThumbnailTranslate(
                            bottomBitmap, collapsedStateHeight, slideOffset, bottomSheet
                        )
                    }
                }
            })

        bottomSheetScrimView = findViewById<BottomSheetScrimView>(R.id.bottom_sheet_scrim_view).apply {
            setOnClickListener(this@ObjectDetectionActivity)
        }

        bottomSheetTitleView = findViewById(R.id.bottom_sheet_title)
        productRecyclerView = findViewById<RecyclerView>(R.id.product_recycler_view).apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@ObjectDetectionActivity)
//            adapter = ProductAdapter(ImmutableList.of())
        }
    }

    private fun setUpWorkflowModel() {
        workflowModel = ViewModelProviders.of(this).get(WorkflowModel::class.java).apply {

            // Observes the workflow state changes, if happens, update the overlay view indicators and
            // camera preview state.
            workflowState.observe(this@ObjectDetectionActivity, Observer { workflowState ->
                if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                    return@Observer
                }
                currentWorkflowState = workflowState
                Log.d(TAG, "Current workflow state: ${workflowState.name}")

//                if (PreferenceUtils.isAutoSearchEnabled(this@ObjectDetectionActivity)) {
//                    stateChangeInAutoSearchMode(workflowState)
//                } else {
                    stateChangeInManualSearchMode(workflowState)
//                }
            })

            // Observes changes on the object to search, if happens, show detected object labels as
            // product search results.
            objectToSearch.observe(this@ObjectDetectionActivity, Observer { detectObject ->
                val productList: List<Product> = detectObject.labels.map { label ->
                    Product("" /* imageUrl */, label.text, "" /* subtitle */)
                }
                workflowModel?.onSearchCompleted(detectObject, productList)
            })

            // Observes changes on the object that has search completed, if happens, show the bottom sheet
            // to present search result.
            searchedObject.observe(this@ObjectDetectionActivity, Observer { searchedObject ->
//                if(searchedObject.productList.find { it.title == "Driver's license" } != null ) {
//                    Toast.makeText(applicationContext, "I found ID card", Toast.LENGTH_SHORT).show()
//                } else {
//                    Toast.makeText(applicationContext, "I didn't found ID card", Toast.LENGTH_SHORT).show()
//                }
//
//                // stop searching & enable take a photo button

                isFirstPhotoTaken = true
                bottomSheetScrimView?.visibility = View.VISIBLE
                objectThumbnailForBottomSheet = searchedObject.getObjectThumbnail()
                Log.e("ON_PHOTO_FOUND", objectThumbnailForBottomSheet.toString())
                val stream = ByteArrayOutputStream()
                objectThumbnailForBottomSheet?.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val byteArray: ByteArray = stream.toByteArray()
                FileHelper.writeImageFileToDisk(applicationContext, byteArray)
//                bottomSheetTitleView?.text = getString(R.string.buttom_sheet_custom_model_title)
//                productRecyclerView?.adapter = ProductAdapter(searchedObject.productList)
                slidingSheetUpFromHiddenState = true
                bottomSheetBehavior?.peekHeight =
                    preview?.height?.div(2) ?: BottomSheetBehavior.PEEK_HEIGHT_AUTO
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED

                takePhotoButton?.visibility = View.VISIBLE
                Handler().postDelayed(Runnable {
                    bottomSheetScrimView?.visibility = View.GONE
//                    bottomSheetScrimView?.animate()
//                        ?.translationY(0f)
//                        ?.alpha(0.0f)
//                        ?.setListener(object : AnimatorListenerAdapter() {
//                            override fun onAnimationEnd(animation: Animator) {
//                                super.onAnimationEnd(animation)
//                                bottomSheetScrimView?.setVisibility(View.GONE)
//                            }
//                        })
                    bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
                }, 3000)
            })
        }
    }

    private fun stateChangeInAutoSearchMode(workflowState: WorkflowState) {
        val wasPromptChipGone = promptChip!!.visibility == View.GONE

        takePhotoButton?.visibility = View.GONE
        when (workflowState) {
            WorkflowState.DETECTING, WorkflowState.DETECTED, WorkflowState.CONFIRMING -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(
                    if (workflowState == WorkflowState.CONFIRMING)
                        R.string.prompt_hold_camera_steady
                    else
                        R.string.prompt_point_at_a_bird
                )
                startCameraPreview()
            }
            WorkflowState.CONFIRMED -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(R.string.prompt_searching)
                stopCameraPreview()
            }
            WorkflowState.SEARCHING -> {
                promptChip?.visibility = View.GONE
                stopCameraPreview()
            }
            WorkflowState.SEARCHED -> {
                stopCameraPreview()
            }
            else -> promptChip?.visibility = View.GONE
        }

        val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip?.visibility == View.VISIBLE
        if (shouldPlayPromptChipEnteringAnimation && promptChipAnimator?.isRunning == false) {
            promptChipAnimator?.start()
        }
    }

    private fun stateChangeInManualSearchMode(workflowState: WorkflowState) {
        val wasPromptChipGone = promptChip?.visibility == View.GONE
        val wasSearchButtonGone = takePhotoButton?.visibility == View.GONE

        when (workflowState) {
            WorkflowState.DETECTING, WorkflowState.DETECTED, WorkflowState.CONFIRMING -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.text = if(!isFirstPhotoTaken) "Наведите камеру на лицевую сторону" else "Наведите камеру на обратную сторону"
                takePhotoButton?.visibility = View.VISIBLE
                startCameraPreview()
            }
            WorkflowState.CONFIRMED -> {
                promptChip?.visibility = View.GONE
//                takePhotoButton?.visibility = View.VISIBLE
//                takePhotoButton?.isEnabled = true
                //this line was committed
//                workflowModel?.onSearchButtonClicked()
//                takePhotoButton?.setBackgroundColor(Color.WHITE)
//                startCameraPreview()
            }
            WorkflowState.SEARCHING -> {
                promptChip?.visibility = View.GONE
                takePhotoButton?.visibility = View.VISIBLE
                takePhotoButton?.isEnabled = true
                takePhotoButton?.setBackgroundColor(Color.GRAY)
                stopCameraPreview()
            }
            WorkflowState.SEARCHED -> {
                promptChip?.visibility = View.GONE
                takePhotoButton?.visibility = View.VISIBLE
                stopCameraPreview()
            }
            else -> {
                promptChip?.visibility = View.GONE
                takePhotoButton?.visibility = View.VISIBLE
            }
        }

        val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip?.visibility == View.VISIBLE
        promptChipAnimator?.let {
            if (shouldPlayPromptChipEnteringAnimation && !it.isRunning) it.start()
        }

//        val shouldPlaySearchButtonEnteringAnimation =  && searchButton?.visibility == View.VISIBLE
//        searchButtonAnimator?.let {
//            if (shouldPlaySearchButtonEnteringAnimation && !it.isRunning) it.start()
//        }
    }

    companion object {
        private const val TAG = "CustomModelODActivity"
        private const val CUSTOM_MODEL_PATH = "custom_models/object_labeler.tflite"
    }
}
