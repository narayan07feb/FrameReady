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
 * A ContentProvider that automatically discovers and registers [FrameReadyInitializer] classes
 * defined as meta-data tags in AndroidManifest.xml under this provider.
 */
class FrameReadyProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val context = context ?: return false
        val packageManager = context.packageManager
        val providerName = ComponentName(context, FrameReadyProvider::class.java)

        val initializersList = mutableListOf<Class<Any>>()

        try {
            val providerInfo = packageManager.getProviderInfo(providerName, PackageManager.GET_META_DATA)
            val metadata = providerInfo.metaData

            if (metadata != null) {
                val keys = metadata.keySet()
                for (key in keys) {
                    val value = metadata.getString(key)
                    if (value == "post_frame_initializer") {
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val clazz = Class.forName(key) as Class<Any>
                            if (FrameReadyInitializer::class.java.isAssignableFrom(clazz)) {
                                initializersList.add(clazz)
                            } else {
                                Log.e(TAG, "Class $key is not an instance of FrameReadyInitializer.")
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

        // Always install the engine to start TTFF tracking, even if the list is empty!
        FrameReady.install(context, initializersList)
        Log.i(TAG, "FrameReady initialized automatically with ${initializersList.size} initializers.")

        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val TAG = "FrameReadyProvider"
    }
}
