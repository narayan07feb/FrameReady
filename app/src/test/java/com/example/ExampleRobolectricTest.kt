package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PostFrameStartup", appName)
  }

  @Test
  fun `launch MainActivity successfully`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java)
    val activity = controller.setup().get()
    assertNotNull(activity)
  }

  @Test
  fun `verify metricsFlow emits value`() = kotlinx.coroutines.test.runTest {
    com.frameready.FrameReady.stableThreshold = 1
    
    val controller = Robolectric.buildActivity(MainActivity::class.java)
    controller.setup()
    
    // Use an explicit timeout to prevent hanging if it fails to emit
    val metrics = kotlinx.coroutines.withTimeoutOrNull(2000L) {
        com.frameready.FrameReady.metricsFlow.first()
    }
    
    println("Captured metrics from Flow: $metrics")
    assertNotNull("metricsFlow should emit a value", metrics)
  }
}
