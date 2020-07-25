package ai.doc.netrunner.fragments

import ai.doc.netrunner.R
import ai.doc.netrunner.utilities.ModelRunnerWatcher
import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

private const val LIVE_CAMERA_PERMISSIONS_REQUEST_CODE = 2001

class WelcomeFragment : Fragment(), ModelRunnerWatcher, ActivityCompat.OnRequestPermissionsResultCallback {

    // Callbacks

    interface Callbacks {
        fun onWelcomeCompleted(didGrantPermissions: Boolean)
    }

    private var callbacks: Callbacks? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callbacks = context as Callbacks?
    }

    override fun onDetach() {
        super.onDetach()
        callbacks = null
    }

    // Creation

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.start_camera_button).setOnClickListener {
            getStarted()
        }
    }

    // Get Started: Request Permissions

    private val requiredPermissions = arrayOf(Manifest.permission.CAMERA)

    // TODO: If "do not ask again" take to settings

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requiredPermissionsGranted()) {
            callbacks?.onWelcomeCompleted(requiredPermissionsGranted())
        }

    }

    private fun requiredPermissionsGranted(): Boolean {
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(requireActivity(), permission!!) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun getStarted() {
        if (requiredPermissionsGranted()) {
            callbacks?.onWelcomeCompleted(true)
        } else {
            requestPermissions(requiredPermissions, LIVE_CAMERA_PERMISSIONS_REQUEST_CODE)
        }
    }

    // Model Runner is Unused

    override fun modelDidChange() {
    }

    override fun stopRunning() {
    }

    override fun startRunning() {
    }

}