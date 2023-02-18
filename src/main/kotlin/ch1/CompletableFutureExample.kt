package ch1

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

internal object CompletableFutureExample {
    fun <T> delay(ms: Int, block: Supplier<T>): CompletableFuture<T> =
        CompletableFuture<T>()
            .completeAsync( block,
                CompletableFuture.delayedExecutor(ms.toLong(), TimeUnit.MILLISECONDS)
            )

    @JvmStatic
    fun main(args: Array<String>) {
        val future1 = delay(1000) { println("Future1 done.");1 }
        val future2 = delay(2000) { println("Future2 done.");2 }
        val future3 = delay(3000) { println("Future3 done.");3 }

        future1.thenAcceptAsync { println("future1: $it") }
        future2.thenAcceptAsync { println("future2: $it") }
        future3.thenAcceptAsync { println("future3: $it") }

        val all = CompletableFuture.allOf(future1, future2, future3)
        all.thenAcceptAsync { println("all DONE!!!!") }

        val any = CompletableFuture.anyOf(future1, future2, future3)
        any.thenAcceptAsync { println("any DONE!!!!") }

        println("Future setup end")

        Thread.sleep(5000)
        println("Main thread end")
    }
}