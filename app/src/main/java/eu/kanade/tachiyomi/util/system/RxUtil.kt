package eu.kanade.tachiyomi.util.system

import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Scheduler
import rx.Single
import rx.Subscription
import kotlin.coroutines.resumeWithException

suspend fun <T> Single<T>.await(subscribeOn: Scheduler? = null): T {
    return suspendCancellableCoroutine { continuation ->
        val self = if (subscribeOn != null) subscribeOn(subscribeOn) else this
        lateinit var sub: Subscription
        sub = self.subscribe(
            {
                continuation.resume(it) {
                    sub.unsubscribe()
                }
            },
            {
                if (!continuation.isCancelled)
                    continuation.resumeWithException(it)
            }
        )

        continuation.invokeOnCancellation {
            sub.unsubscribe()
        }
    }
}
