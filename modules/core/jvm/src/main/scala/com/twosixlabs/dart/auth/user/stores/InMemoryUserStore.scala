package com.twosixlabs.dart.auth.user.stores

import com.twosixlabs.dart.auth.groups.DartGroup
import com.twosixlabs.dart.auth.user.DartUserStore.{InvalidUserNameException, UserAlreadyExistsException, UserNotFoundException, ValidUser}
import com.twosixlabs.dart.auth.user.{DartUser, DartUserStore, UserMod}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class InMemoryUserStore extends DartUserStore {
    override implicit val ec : ExecutionContext = scala.concurrent.ExecutionContext.global

    private val userMap : mutable.Map[ String, DartUser ] = mutable.Map.empty

    override def allUsers : Future[ Set[ DartUser ] ] = Future( userMap.values.toSet )

    override def addUsers( users : Seq[ UserMod ] ) : Future[ Unit ] = {
        if ( users.isEmpty ) Future.successful()
        else {
            val failTest : Option[ Throwable ] = users.foldLeft[ Option[ Throwable ] ]( None )( ( failed, v) => if ( failed.isDefined) failed else v.user.userName match {
                case ValidUser( userName ) =>
                    if ( userMap.contains( userName ) ) {
                        println( s"USER ALREADY EXISTS $userName")
                        Some( new UserAlreadyExistsException( userName ) )
                    }
                    else failed
                case badUserName =>
                    println( s"BAD USER NAME: $badUserName" )
                    Some( new InvalidUserNameException( badUserName ) )
            } )
            if ( failTest.isDefined ) Future.failed( failTest.get )
            else Future( users.foreach( v => userMap( v.user.userName ) = v.user ) )
        }
    }

    override def removeUsers( names : Seq[ String ] ) : Future[ Unit ] = Future {
        names.foreach( v => {
            if ( !userMap.contains( v ) ) throw new UserNotFoundException( v )
        } )
        names.foreach( v => userMap -= v )
    }

    override def updateUsers( users : Seq[ UserMod ] ) : Future[ Unit ] = Future {
        users.foreach( v => {
            if ( !userMap.contains( v.user.userName ) ) throw new UserNotFoundException( v.user.userName )
        } )
        users.foreach( v => userMap( v.user.userName ) = v.user )
    }
}
