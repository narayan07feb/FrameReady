import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.BufferOverflow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch

fun main() = runBlocking {
    val flow = MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    println("tryEmit: " + flow.tryEmit("Hello"))
    
    val job = launch {
        flow.collect {
            println("Collected: $it")
        }
    }
    
    kotlinx.coroutines.delay(100)
    job.cancel()
}
