package com.frameready

/**
 * Lock-protected map/set/flag primitives used by the shared [FrameReady] engine so its
 * concurrency guarantees are identical on every platform — no per-platform re-implementation,
 * no drift between an Android "thread-safe" copy and an iOS one that forgot to be.
 */

internal class SafeMap<K : Any, V : Any> {
    private val lock = PlatformLock()
    private val map = HashMap<K, V>()

    fun getOrPut(key: K, default: () -> V): V = lock.withLock { map.getOrPut(key, default) }
    operator fun get(key: K): V? = lock.withLock { map[key] }
    operator fun set(key: K, value: V) { lock.withLock { map[key] = value } }
    fun remove(key: K): V? = lock.withLock { map.remove(key) }
    fun containsKey(key: K): Boolean = lock.withLock { map.containsKey(key) }
    fun clear() = lock.withLock { map.clear() }
    fun snapshotValues(): List<V> = lock.withLock { map.values.toList() }
    val size: Int get() = lock.withLock { map.size }
}

internal class SafeSet<T : Any> {
    private val lock = PlatformLock()
    private val set = LinkedHashSet<T>()

    fun add(item: T): Boolean = lock.withLock { set.add(item) }
    fun addAll(items: Collection<T>) = lock.withLock { set.addAll(items) }
    fun contains(item: T): Boolean = lock.withLock { set.contains(item) }
    fun clear() = lock.withLock { set.clear() }
    fun toList(): List<T> = lock.withLock { set.toList() }
    fun forEachSnapshot(action: (T) -> Unit) = lock.withLock { set.toList() }.forEach(action)
    val isEmpty: Boolean get() = lock.withLock { set.isEmpty() }
    val size: Int get() = lock.withLock { set.size }
}

/** A CAS-capable boolean flag, portable across platforms without a stdlib atomics dependency. */
internal class SafeFlag(initial: Boolean = false) {
    private val lock = PlatformLock()
    private var value = initial

    fun get(): Boolean = lock.withLock { value }
    fun set(v: Boolean) = lock.withLock { value = v }

    /** Sets to [newValue] iff current value equals [expected]; returns whether it changed. */
    fun compareAndSet(expected: Boolean, newValue: Boolean): Boolean = lock.withLock {
        if (value == expected) {
            value = newValue
            true
        } else {
            false
        }
    }
}

/** A CAS-capable long counter, used for lightweight monotonic offsets. */
internal class SafeLongRef(initial: Long = 0L) {
    private val lock = PlatformLock()
    private var value = initial

    fun get(): Long = lock.withLock { value }
    fun set(v: Long) = lock.withLock { value = v }
    fun compareAndSet(expected: Long, newValue: Long): Boolean = lock.withLock {
        if (value == expected) {
            value = newValue
            true
        } else {
            false
        }
    }
    fun incrementAndGet(): Long = lock.withLock { ++value }
    fun decrementAndGet(): Long = lock.withLock { --value }
}
