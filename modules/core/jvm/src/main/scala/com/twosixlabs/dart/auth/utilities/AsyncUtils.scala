package com.twosixlabs.dart.auth.utilities

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object AsyncUtils {

    def retry[ T ]( futureAttempt : ( ) => Future[ T ], times : Int, delay : Duration )( implicit ec : ExecutionContext ) : Future[ T ] = {
        futureAttempt() transformWith {
            case Success( res ) => Future.successful( res )
            case Failure( e ) =>
                if ( times <= 1 ) Future.failed( e )
                else {
                    Thread.sleep( delay.toMillis )
                    retry( futureAttempt, times - 1, delay )
                }
        }
    }

}
