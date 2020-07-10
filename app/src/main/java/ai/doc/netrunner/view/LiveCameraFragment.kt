package ai.doc.netrunner.view

import ai.doc.netrunner.R
import ai.doc.netrunner.databinding.FragmentLiveCameraBinding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.util.Log
import android.util.Size
import android.view.*
import android.view.TextureView.SurfaceTextureListener

import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment

import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

// TODO: Support to pause and resume camera
// TODO: Support to facing direction

open class LiveCameraFragment : Fragment(), OnRequestPermissionsResultCallback {

    /**
     * Compares two `Size`s based on their areas.
     */

    private class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(
                    lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }
    }

    companion object {
        private const val TAG = "TfLiteCameraDemo"
        private const val PERMISSIONS_REQUEST_CODE = 1

        /**
         * Max preview width and height that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_WIDTH = 1920
        private const val MAX_PREVIEW_HEIGHT = 1080

        /**
         * Resizes image.
         *
         *
         * Attempting to use too large a preview size could  exceed the camera bus' bandwidth limitation,
         * resulting in gorgeous previews but the storage of garbage capture data.
         *
         *
         * Given `choices` of `Size`s supported by a camera, choose the smallest one that is
         * at least as large as the respective texture view size, and that is at most as large as the
         * respective max size, and whose aspect ratio matches with the specified value. If such size
         * doesn't exist, choose the largest one that is at most as large as the respective max size, and
         * whose aspect ratio matches with the specified value.
         *
         * @param choices           The list of sizes that the camera supports for the intended output class
         * @param textureViewWidth  The width of the texture view relative to sensor coordinate
         * @param textureViewHeight The height of the texture view relative to sensor coordinate
         * @param maxWidth          The maximum width that can be chosen
         * @param maxHeight         The maximum height that can be chosen
         * @param aspectRatio       The aspect ratio
         * @return The optimal `Size`, or an arbitrary one if none were big enough
         */
        private fun chooseOptimalSize(
                choices: Array<Size>,
                textureViewWidth: Int,
                textureViewHeight: Int,
                maxWidth: Int,
                maxHeight: Int,
                aspectRatio: Size): Size {

            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough: MutableList<Size> = ArrayList()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough: MutableList<Size> = ArrayList()
            val w = aspectRatio.width
            val h = aspectRatio.height
            for (option in choices) {
                if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * h / w) {
                    if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            return if (bigEnough.size > 0) {
                Collections.min(bigEnough, CompareSizesByArea())
            } else if (notBigEnough.size > 0) {
                Collections.max(notBigEnough, CompareSizesByArea())
            } else {
                Log.e(TAG, "Couldn't find any suitable preview size")
                choices[0]
            }
        }
    }

    private var checkedPermissions = false
    protected val cameraFacing: Int
        protected get() = CameraCharacteristics.LENS_FACING_BACK

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a [ ].
     */

    private val surfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    /**
     * ID of the current [CameraDevice].
     */

    private var cameraId: String? = null

    /**
     * An [TextureView] for camera preview.
     */

    private lateinit var textureView: TextureView

    /**
     * A [CameraCaptureSession] for camera preview.
     */

    private var captureSession: CameraCaptureSession? = null

    /**
     * A reference to the opened [CameraDevice].
     */

    private var cameraDevice: CameraDevice? = null

    /**
     * The [android.util.Size] of camera preview.
     */

    private var previewSize: Size? = null

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     */

    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(currentCameraDevice: CameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            cameraOpenCloseLock.release()
            cameraDevice = currentCameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(currentCameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            currentCameraDevice.close()
            cameraDevice = null
        }

        override fun onError(currentCameraDevice: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            currentCameraDevice.close()
            cameraDevice = null
            val activity: Activity? = activity
            activity?.finish()
        }
    }

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    /**
     * A [Handler] for running tasks in the background.
     */
    /**
     * An [ImageReader] that handles image capture.
     */

    private var imageReader: ImageReader? = null

    /**
     * [CaptureRequest.Builder] for the camera preview
     */

    private var previewRequestBuilder: CaptureRequest.Builder? = null

    /**
     * [CaptureRequest] generated by [.previewRequestBuilder]
     */

    private var previewRequest: CaptureRequest? = null

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */

    private val cameraOpenCloseLock = Semaphore(1)

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles events related to capture.
     */

    private val captureCallback: CaptureCallback = object : CaptureCallback() {
        override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult) {
        }

        override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult) {
        }
    }

    private val backgroundThread: HandlerThread? = null
    private val backgroundHandler: Handler? = null

    /**
     * Layout the preview and buttons.
     */

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding: FragmentLiveCameraBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_live_camera, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById(R.id.texture)
    }

    // TODO: when are onResume and onPause called?

    override fun onResume() {
        super.onResume()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).

        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        closeCamera()
        super.onPause()
    }

    override fun onDestroy() { /*
        if (classifier != null) {
            classifier.close();
        }*/
        super.onDestroy()
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */

    private fun setUpCameraOutputs(width: Int, height: Int) {
        val activity: Activity? = activity
        val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing != cameraFacing) {
                    continue
                }
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?: continue

                // // For still image captures, we use the largest available size.
                val largest = Collections.max(
                        Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)), CompareSizesByArea())
                imageReader = ImageReader.newInstance(
                        largest.width, largest.height, ImageFormat.JPEG,  /*maxImages*/2)

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                val displayRotation = activity.windowManager.defaultDisplay.rotation
                // noinspection ConstantConditions
                /* Orientation of the camera sensor */
                val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                var swappedDimensions = false
                when (displayRotation) {
                    Surface.ROTATION_0, Surface.ROTATION_180 -> if (sensorOrientation == 90 || sensorOrientation == 270) {
                        swappedDimensions = true
                    }
                    Surface.ROTATION_90, Surface.ROTATION_270 -> if (sensorOrientation == 0 || sensorOrientation == 180) {
                        swappedDimensions = true
                    }
                    else -> Log.e(TAG, "Display rotation is invalid: $displayRotation")
                }
                val displaySize = Point()
                activity.windowManager.defaultDisplay.getSize(displaySize)
                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height
                var maxPreviewWidth = displaySize.x
                var maxPreviewHeight = displaySize.y
                if (swappedDimensions) {
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width
                    maxPreviewWidth = displaySize.y
                    maxPreviewHeight = displaySize.x
                }
                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH
                }
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT
                }
                previewSize = chooseOptimalSize(
                        map.getOutputSizes(SurfaceTexture::class.java),
                        rotatedPreviewWidth,
                        rotatedPreviewHeight,
                        maxPreviewWidth,
                        maxPreviewHeight,
                        largest)
                this.cameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to access Camera", e)
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, e.toString())
        }
    }

    private val requiredPermissions: Array<String?>
        get() {
            val activity: Activity? = activity
            return try {
                val info = activity!!
                        .getPackageManager()
                        .getPackageInfo(activity!!.packageName, PackageManager.GET_PERMISSIONS)
                val ps = info.requestedPermissions
                if (ps != null && ps.size > 0) {
                    ps
                } else {
                    arrayOfNulls(0)
                }
            } catch (e: Exception) {
                arrayOfNulls(0)
            }
        }

    private fun openCamera(width: Int, height: Int) {

        checkedPermissions = if (!checkedPermissions && !allPermissionsGranted()) {
            ActivityCompat.requestPermissions(activity!!, requiredPermissions, PERMISSIONS_REQUEST_CODE)
            return
        } else {
            true
        }

        setUpCameraOutputs(width, height)
        configureTransform(width, height)

        val activity: Activity? = activity
        val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            if (ActivityCompat.checkSelfPermission(getActivity()!!, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            manager.openCamera(cameraId, stateCallback, null) // TODO: change to other handler
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open Camera", e)
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(activity!!, permission!!) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /**
     * Closes the current [CameraDevice].
     */
    protected fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            if (null != captureSession) {
                captureSession!!.close()
                captureSession = null
            }
            if (null != cameraDevice) {
                cameraDevice!!.close()
                cameraDevice = null
            }
            if (null != imageReader) {
                imageReader!!.close()
                imageReader = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */

    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture!!

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder?.addTarget(surface)

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice!!.createCaptureSession(
                    Arrays.asList(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return
                            }

                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilder?.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                                // Finally, we start displaying the camera preview.
                                previewRequest = previewRequestBuilder?.build()
                                captureSession!!.setRepeatingRequest(
                                        previewRequest, captureCallback, null) // TODO: change to other handler
                            } catch (e: CameraAccessException) {
                                Log.e(TAG, "Failed to set up config to capture Camera", e)
                            }
                        }

                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                            showToast("Failed")
                        }
                    },
                    null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to preview Camera", e)
        }
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`. This
     * method should be called after the camera preview size is determined in setUpCameraOutputs and
     * also the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val activity: Activity? = activity

        if (null == textureView || null == previewSize || null == activity) {
            return
        }

        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0.0f, 0.0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0.0f, 0.0f, previewSize!!.height.toFloat(), previewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    viewHeight.toFloat() / previewSize!!.height,
                    viewWidth.toFloat() / previewSize!!.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90 * (rotation - 2).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }

        textureView.setTransform(matrix)
    }

    private fun showToast(s: String) {
        val builder = SpannableStringBuilder()
        val str1 = SpannableString(s)
        builder.append(str1)
        showToast(builder)
    }

    private fun showToast(builder: SpannableStringBuilder) {
        Log.i("LiveCameraFragment", builder.toString())
    }
}