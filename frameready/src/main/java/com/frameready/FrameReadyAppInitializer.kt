package com.frameready

import android.content.Context
import androidx.startup.Initializer

/**
 * An App Startup [Initializer] that acts as a bridge, letting androidx.startup
 * transition handoffs to FrameReady seamlessly at application start.
 */
class FrameReadyAppInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // This acts as a bridge. It triggers auto-install behavior or manual hook.
        // In highly customized apps, this ensures that the pre-frame pipeline hands off control
        // safely to the post-frame architecture.
        FrameReady.install(context, emptyList())
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}
