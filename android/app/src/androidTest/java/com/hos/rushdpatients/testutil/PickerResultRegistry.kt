package com.hos.rushdpatients.testutil

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat

/** Exercises production OpenDocument registration/result handling without external picker UI. */
class PickerResultRegistry(private val context: Context) : ActivityResultRegistry(), ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry get() = this
    var launchedIntent: Intent? = null
        private set
    private var pendingCode: Int? = null
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
        options: ActivityOptionsCompat?) {
        check(pendingCode == null) { "Duplicate document launch" }
        launchedIntent = contract.createIntent(context, input)
        pendingCode = requestCode
    }
    fun deliver(uri: Uri?) {
        val code = checkNotNull(pendingCode) { "No pending document request" }
        pendingCode = null
        dispatchResult(code, if (uri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            uri?.let { Intent().setData(it).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) })
    }
}
