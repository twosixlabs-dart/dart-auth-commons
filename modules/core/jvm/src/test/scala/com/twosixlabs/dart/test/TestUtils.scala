package com.twosixlabs.dart.test

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, DurationLong}
import scala.language.postfixOps

trait TestUtils {

    implicit class AwaitableFuture[ T ]( fut : Future[ T ] ) {
        def await( duration : Duration ) : T = Await.result( fut, duration )

        def await( ms : Long ) : T = await( ms.milliseconds )

        def await : T = await( 10 seconds )

        def awaitWrite( duration : Duration, writeDelay : Duration ) : T = {
            val res = Await.result( fut, duration )
            Thread.sleep( writeDelay.toMillis )
            res
        }

        def awaitWrite( duration : Long, writeDelay : Long ) : T = awaitWrite( duration.milliseconds, writeDelay.milliseconds )

        def awaitWrite( writeDelay : Duration ) : T = awaitWrite( 10 seconds, writeDelay )

        def awaitWrite( writeDelay : Long ) : T = awaitWrite( 10000, writeDelay )

        def awaitWrite : T = awaitWrite( 5.seconds )
    }

}

object TestUtils extends TestUtils
