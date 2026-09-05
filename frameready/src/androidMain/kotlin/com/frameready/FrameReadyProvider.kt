package com.frameready

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Process
import android.util.Log

/**
 * Auto-discovers initializer **class names** from `<meta-data>` under this provider, then
 * [FrameReady.install]. [Class.forName] and constructors run at first frame, not here.
 */
class FrameReadyProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val context = context ?: return false
        FrameReady.processStartMs = Process.getStartUptimeMillis()

        val names = mutableListOf<String>()
        try {
            val providerInfo = context.packageManager.getProviderInfo(
                ComponentName(context, FrameReadyProvider::class.java),
                PackageManager.GET_META_DATA
            )
            val metadata = providerInfo.metaData
            if (metadata != null) {
                for (key in metadata.keySet()) {
                    if (metadata.getString(key) == "post_frame_initializer") {
                        names.add(key)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read Metadata for auto-discovery.", e)
        }

        FrameReady.install(context, emptyList<Class<Any>>())
        FrameReady.enqueueManifestInitializerNames(names)
        Log.i(TAG, "FrameReady initialized automatically with ${names.size} initializers.")
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val TAG = "FrameReadyProvider"
    }
}
