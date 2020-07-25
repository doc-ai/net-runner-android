package ai.doc.netrunner.utilities

import ai.doc.netrunner.R
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment


object PermissionsManager {

    // Open Settings

    fun openSettings(fragment: Fragment) {
        fragment.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", fragment.requireActivity().packageName, null)
        })
    }

    fun openSettings(activity: Activity) {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        })
    }

    // Camera Permissions

    private const val cameraPermission = Manifest.permission.CAMERA

    fun hasCameraPermissions(activity: Activity): Boolean {
        return ActivityCompat.checkSelfPermission(activity, cameraPermission) == PackageManager.PERMISSION_GRANTED
    }

    fun requestCameraPermissions(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, arrayOf(cameraPermission), requestCode)
    }

    fun requestCameraPermissions(fragment: Fragment, requestCode: Int) {
        fragment.requestPermissions(arrayOf(cameraPermission), requestCode)
    }

    fun neverAskCameraPermissionsAgain(activity: Activity): Boolean {
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, cameraPermission)
    }

    fun showCameraRationale(activity: Activity) {
        AlertDialog.Builder(activity).apply {
            setTitle(R.string.welcome_dialog_go_to_settings_title)
            setMessage(R.string.welcome_dialog_go_to_settings_message)

            setPositiveButton(R.string.dialog_go_to_settings_button) { dialog, _ ->
                openSettings(activity)
                dialog.dismiss()
            }
            setNegativeButton(R.string.dialog_cancel_button) { dialog, _ ->
                dialog.cancel()
            }
        }.show()
    }

    // Image Gallery Permissions

    private const val imageGalleryPermission = Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasImageGalleryPermissions(activity: Activity): Boolean {
        return ActivityCompat.checkSelfPermission(activity, imageGalleryPermission) == PackageManager.PERMISSION_GRANTED
    }

    fun requestImageGalleryPermissions(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, arrayOf(imageGalleryPermission), requestCode)
    }

    fun requestImageGalleryPermissions(fragment: Fragment, requestCode: Int) {
        fragment.requestPermissions(arrayOf(imageGalleryPermission), requestCode)
    }

    fun neverAskImageGalleryPermissionsAgain(activity: Activity): Boolean {
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, imageGalleryPermission)
    }

    fun showImageGalleryRationale(activity: Activity) {
        AlertDialog.Builder(activity).apply {
            setTitle(R.string.image_gallery_dialog_go_to_settings_title)
            setMessage(R.string.image_gallery_dialog_go_to_settings_message)

            setPositiveButton(R.string.dialog_go_to_settings_button) { dialog, _ ->
                openSettings(activity)
                dialog.dismiss()
            }
            setNegativeButton(R.string.dialog_cancel_button) { dialog, _ ->
                dialog.cancel()
            }
        }.show()
    }
}