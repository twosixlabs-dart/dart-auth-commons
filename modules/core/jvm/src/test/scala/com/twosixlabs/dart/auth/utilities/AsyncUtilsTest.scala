package com.twosixlabs.dart.auth.utilities

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationDouble
import scala.language.postfixOps

class AsyncUtilsTest extends AnyFlatSpecLike with Matchers {

    behavior of "AsyncUtils.retry"

    it should "retry a future until it completes as long as it completes within the max number of attempts given delay" in {
        val startTime = System.currentTimeMillis()

        def futureAttempt() : Future[ Int ] = Future {
            Thread.sleep( 250 )
            if ( System.currentTimeMillis() - startTime < 1000 ) throw new Exception
            else 5
        }

        val res = Await.result( AsyncUtils.retry( futureAttempt, 3, 0.5 seconds ), 3.5 seconds )
        res shouldBe 5
    }

    it should "fail if future fails after max retries" in {
        val startTime = System.currentTimeMillis()

        def futureAttempt() : Future[ Int ] = Future {
            Thread.sleep( 250 )
            if ( System.currentTimeMillis() - startTime < 10000 ) throw new Exception
            else 5
        }

        an [ Exception ] should be thrownBy ( Await.result( AsyncUtils.retry( futureAttempt, 3, 0.5 seconds ), 3.5 seconds ) )
    }

}
