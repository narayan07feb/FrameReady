import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

fun main() = runBlocking {
    val flow = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    println("tryEmit: " + flow.tryEmit("Hello"))
    
    val job = launch {
        flow.collect {
            println("Collected: $it")
        }
    }
    
    delay(100)
    job.cancel()
}
