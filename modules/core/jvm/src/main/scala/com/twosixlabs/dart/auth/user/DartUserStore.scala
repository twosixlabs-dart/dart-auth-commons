package com.twosixlabs.dart.auth.user

import com.twosixlabs.dart.auth.groups.DartGroup
import com.twosixlabs.dart.auth.user.DartUserStore.UserNotFoundException

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.matching.Regex

case class UserMod( user : DartUser, password : Option[ String ] = None )

object UserMod {
    implicit def userToMod( user : DartUser ) : UserMod = UserMod( user )
}

trait DartUserStore {
    implicit val ec : ExecutionContext

    def allUsers : Future[ Set[ DartUser ] ]

    def user( name: String ) : Future[ DartUser ] = allUsers.map( _.find( _.userName == name ).getOrElse( throw new UserNotFoundException( name ) ) )
    def userGroups( name : String ) : Future[ Set[ DartGroup ] ] = user( name ).map( _.groups )
    def userInfo( name : String ) : Future[ DartUserInfo ] = user( name ).map( _.userInfo )

    def groupUsers( group : DartGroup ) : Future[ Set[ DartUser ] ] = allUsers.map( _.filter( _.groups.contains( group ) ).toSet )

    def addUsers( users : Seq[ UserMod ] ) : Future[ Unit ]
    def addUser( user : UserMod, otherUsers : UserMod* ) : Future[ Unit ] = addUsers( user +: otherUsers )

    def removeUsers( names : Seq[ String ] ) : Future[ Unit ]
    def removeUser( name : String, otherNames : String* ) : Future[ Unit ] = removeUsers( name +: otherNames )
    def removeUser( user : DartUser, otherUsers : DartUser* ) : Future[ Unit ] = removeUser( user.userName, otherUsers.map( _.userName ) : _* )

    def updateUsers( users : Seq[ UserMod ] ) : Future[ Unit ]
    def updateUser( user : UserMod, otherUsers : UserMod* ) : Future[ Unit ] = updateUsers( user +: otherUsers )

    def addUserToGroups( userName : String, groups : Seq[ DartGroup ] ) : Future[ Unit ] = for {
        u <- user( userName )
        _ <- updateUser( u.copy( groups = u.groups ++ groups.toSet ) )
    } yield ()
    def addUserToGroups( user : DartUser, groups : Seq[ DartGroup ] ) : Future[ Unit ] = updateUser( user.copy( groups = user.groups ++ groups.toSet ) )
    def addUserToGroup( userName : String, group : DartGroup, otherGroups : DartGroup* ) : Future[ Unit ] = addUserToGroups( userName, group +: otherGroups )
    def addUserToGroup( user : DartUser, group : DartGroup, otherGroups : DartGroup* ) : Future[ Unit ] = addUserToGroups( user.userName, group +: otherGroups )

    def updateUserPassword( userName : String, newPassword : String ) : Future[ Unit ] = for {
        u <- user( userName )
        _ <- updateUser( UserMod( u, Some( newPassword ) ) )
    } yield ()

    def removeUserFromGroups( userName : String, groups : Seq[ DartGroup ] ) : Future[ Unit ] = user( userName ).flatMap( user => updateUser( user.copy( groups = user.groups &~ groups.toSet ) ) )
    def removeUserFromGroups( user : DartUser, groups : Seq[ DartGroup ] ) : Future[ Unit ] = removeUserFromGroups( user.userName, groups )
    def removeUserFromGroup( userName : String, group : DartGroup, otherGroups : DartGroup* ) : Future[ Unit ] = removeUserFromGroups( userName, group +: otherGroups )
    def removeUserFromGroup( user : DartUser, group : DartGroup, otherGroups : DartGroup* ) : Future[ Unit ] = removeUserFromGroups( user.userName, group +: otherGroups )

    implicit class DartUserWithStore( dartUser : DartUser ) {
        def addToStore() : Future[ Unit ] = addUser( dartUser )
        def removeFromStore() : Future[ Unit ] = removeUser( dartUser )

        def getLatestFromStore : Future[ DartUser ] = user( dartUser.userName )

        def update( userInfo : DartUserInfo = dartUser.userInfo, groups : Set[ DartGroup ] = dartUser.groups ) : Future[ Unit ] = {
            updateUser( dartUser.copy( userInfo = userInfo, groups = groups ) )
        }

        def updatePassword( newPassword : String ) : Future[ Unit ] = updateUserPassword( dartUser.userName, newPassword )

        def addToGroups( groups : Seq[ DartGroup ] ) : Future[ Unit ] = addUserToGroups( dartUser, groups )
        def addToGroup( group : DartGroup, otherGroups : DartGroup* ) : Future[ Unit ] = addUserToGroups( dartUser, group +: otherGroups )

        def removeFromGroups( groups : Seq[ DartGroup ] ) : Future[ Unit ] = removeUserFromGroups( dartUser, groups )
        def removeFromGroup( group : DartGroup, otherGroups : DartGroup* ) : Future[ Unit ] = removeUserFromGroups( dartUser, group +: otherGroups )
    }

    implicit class DartGroupWithUserStore( dartGroup : DartGroup ) {
        def users : Future[ Set[ DartUser ] ] = groupUsers( dartGroup )

        def addUser( usr : DartUser ) : Future[ Unit ] = addUserToGroup( usr, dartGroup )

        def removeUser( user : DartUser ) : Future[ Unit ] = removeUserFromGroup( user, dartGroup )
    }
}

object DartUserStore {
    val ValidUser : Regex = """^([a-z0-9\-]+)$""".r

    class DartUserStoreException( message : String, cause : Throwable = null ) extends Exception( message, cause )

    class UserNotFoundException( val userName : String, cause : Throwable = null ) extends DartUserStoreException( s"User does not exist with name $userName", cause )
    class UserAlreadyExistsException( val userName : String ) extends DartUserStoreException( s"User already exists with name: $userName" )
    class InvalidUserNameException( val userName : String ) extends DartUserStoreException( s"User name ${userName} is not valid. Acceptable format: ${ValidUser.toString}." )
    class UserNotInGroupException( val userName : String, val group : DartGroup ) extends DartUserStoreException( s"User $userName is not in group $group" )
    class GroupNotFoundException( val group : DartGroup ) extends DartUserStoreException( s"Group $group does not exist" )

}
