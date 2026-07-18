package com.frameready

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * Auto-discovers and registers [FrameReadyInitializer] classes declared as `<meta-data>` tags
 * under this provider in AndroidManifest.xml, then calls [FrameReady.install].
 */
class FrameReadyProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val context = context ?: return false
        val providerName = ComponentName(context, FrameReadyProvider::class.java)
        val initializersList = mutableListOf<Class<Any>>()

        try {
            val providerInfo = context.packageManager.getProviderInfo(providerName, PackageManager.GET_META_DATA)
            val metadata = providerInfo.metaData
            if (metadata != null) {
                for (key in metadata.keySet()) {
                    if (metadata.getString(key) == "post_frame_initializer") {
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val clazz = Class.forName(key) as Class<Any>
                            if (FrameReadyInitializer::class.java.isAssignableFrom(clazz)) {
                                initializersList.add(clazz)
                            } else {
                                Log.e(TAG, "Class $key is not a FrameReadyInitializer.")
                            }
                        } catch (e: ClassNotFoundException) {
                            Log.e(TAG, "Failed to find initializer class: $key", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read Metadata for auto-discovery.", e)
        }

        FrameReady.install(context, initializersList)
        Log.i(TAG, "FrameReady initialized automatically with ${initializersList.size} initializers.")
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
