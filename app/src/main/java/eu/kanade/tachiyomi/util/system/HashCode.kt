package eu.kanade.tachiyomi.util.system

/**Generate a unique hash code*/
class HashCode {
    companion object {
        fun <T> generate(vararg thingsToHash: T): Int {
            var hash = 17
            for (t in thingsToHash) {
                hash = hash * 31 + t.hashCode()
            }
            return hash
        }
    }
}
