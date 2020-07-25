package ai.doc.netrunner.fragments

import ai.doc.netrunner.R
import ai.doc.netrunner.utilities.ModelRunnerWatcher
import ai.doc.netrunner.utilities.PermissionsManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
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

        view.findViewById<Button>(R.id.get_started_button).setOnClickListener {
            getStarted()
        }
    }

    // Get Started: Request Permissions

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (PermissionsManager.hasCameraPermissions(requireActivity())) {
            callbacks?.onWelcomeCompleted(true)
        } else if (PermissionsManager.neverAskCameraPermissionsAgain(requireActivity())) {
            showCameraRationale()
        }
    }

    private fun getStarted() {
        if (PermissionsManager.hasCameraPermissions(requireActivity())) {
            callbacks?.onWelcomeCompleted(true)
        } else {
            PermissionsManager.requestCameraPermissions(this, LIVE_CAMERA_PERMISSIONS_REQUEST_CODE)
        }
    }

    private fun showCameraRationale() {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(getString(R.string.welcome_dialog_go_to_settings_title))
            setMessage(getString(R.string.welcome_dialog_go_to_settings_message))

            setPositiveButton(R.string.dialog_go_to_settings_button) { dialog, _ ->
                PermissionsManager.openSettings(this@WelcomeFragment)
                dialog.dismiss()
            }
            setNegativeButton(R.string.dialog_cancel_button) { dialog, _ ->
                dialog.cancel()
            }
        }.show()
    }

    // Model Runner is Unused

    override fun modelDidChange() {
    }

    override fun stopRunning() {
    }

    override fun startRunning() {
    }

}