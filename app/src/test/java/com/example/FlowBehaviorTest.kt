package com.example
import org.junit.Test
import org.junit.Assert.assertEquals
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch

class FlowBehaviorTest {
    @Test
    fun testReplay() = runBlocking {
        val flow = MutableSharedFlow<String>(
            replay = 1,
            extraBufferCapacity = 0,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        
        flow.tryEmit("Hello")
        
        val value = flow.first()
        assertEquals("Hello", value)
    }
}
