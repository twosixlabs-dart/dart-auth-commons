package com.twosixlabs.dart.auth.keycloak

import akka.util.ByteString
import com.fullfacing.akka.monix.task.backend.AkkaMonixHttpBackend
import com.fullfacing.keycloak4s.admin.monix.client.{Keycloak, KeycloakClient}
import com.fullfacing.keycloak4s.core.models.enums.CredentialTypes
import com.fullfacing.keycloak4s.core.models.{ConfigWithAuth, Credential, Group, KeycloakConfig, User}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import sttp.client.{NothingT, SttpBackend}

import java.util.UUID
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class KeycloakAdminClient( scheme : String,
                                host : String,
                                port : Int,
                                basePath : List[ String ] = List( "auth" ),
                                adminRealm : String,
                                adminClientId : String,
                                adminClientSecret : String,
                                realm : String ) {

    implicit val keycloakClient : KeycloakClient[ ByteString ] = {
        val authConfig = KeycloakConfig.Secret(
            realm = adminRealm,
            clientId = adminClientId,
            clientSecret = adminClientSecret
            )
        val keycloakConfig = ConfigWithAuth(
            scheme = scheme,
            host = host,
            port = port,
            realm = realm,
            authn = authConfig
            )
        implicit val backend : SttpBackend[ Task, Observable[ ByteString ], NothingT ] = AkkaMonixHttpBackend()
        new KeycloakClient( keycloakConfig )
    }
    private val groupService = Keycloak.Groups
    private val userService = Keycloak.Users

    def createGroupAndRetrieve( name : String, subGroups : List[ String ] = List() ) : Future[ Group ] = {
        for {
            group <- groupService.createAndRetrieve( Group.Create( name ) ).runToFuture.transformWith {
                case Success( Left( e ) ) => Future.failed( e )
                case Success( Right( group ) ) => Future.successful( group )
                case Failure( e ) => Future.failed( e )
            }
            _ <- Future.sequence( subGroups.map( subGroup => addSubGroup( group.id, subGroup ) ) )
        } yield group
    }

    def addSubGroup( groupId : UUID, subGroupName : String ) : Future[ Group ] = {
        groupService.createSubGroup( groupId, Group.Create( subGroupName ) ).runToFuture.transformWith {
            case Success( Left( e ) ) => Future.failed( e )
            case Success( Right( group ) ) => Future.successful( group )
            case Failure( e ) => Future.failed( e )
        }
    }

    def getAllGroups : Future[ Seq[ Group ] ] = {
        groupService.fetch().runToFuture.transformWith {
            case Success( Left( e ) ) => Future.failed( e )
            case Success( Right( groups ) ) => Future.successful( groups )
            case Failure( e ) => Future.failed( e )
        }
    }

    def getGroup( groupName : String ) : Future[ Option[ Group ] ] = {
        groupService.fetchL( search = Some( groupName ) ).runToFuture.transformWith {
            case Success( groups ) => Future.successful( groups.find( group => group.name.equals( groupName ) ) )
            case Failure( e ) => Future.failed( e )
        }
    }

    def deleteGroup( groupId : UUID ) : Future[ Unit ] = {
        groupService.delete( groupId = groupId ).runToFuture.transformWith {
            case Success( Left( e ) ) => Future.failed( e )
            case Success( Right( group ) ) => Future.successful( group )
            case Failure( e ) => Future.failed( e )
        }
    }

    def generateCredentials( password : String ) : Credential = {
        val credentialType = CredentialTypes.Password
        Credential( `type` = Some( credentialType ),
                    temporary = Some( false ),
                    value = Some( password ) )
    }

    def createUserAndRetrieve( name : String, firstName : Option[ String ], lastName : Option[ String ], email : Option[ String ], password : Option[ String ] = None ) : Future[ User ] = {
        val credentials = password.map( generateCredentials ).toList

        userService.createAndRetrieve( User.Create( username = name,
                                                    firstName = firstName,
                                                    lastName = lastName,
                                                    email = email,
                                                    credentials = credentials,
                                                    enabled = true ) ).runToFuture.transformWith {
            case Success( Left( e ) ) => Future.failed( e )
            case Success( Right( user ) ) => Future.successful( user )
            case Failure( e ) => Future.failed( e )
        }
    }

    def updateUser( name : String, firstName : Option[ String ], lastName : Option[ String ], email : Option[ String ], password : Option[ String ] = None ) : Future[ User ] = {
        val credentials = password.map( v => List( generateCredentials( v ) ) )

        for {
            user <- getUser( name )
            _ <- userService.update( user.id, User.Update( firstName = firstName,
                                                           lastName = lastName,
                                                           email = email,
                                                           credentials = credentials,
                                                           enabled = Some( true ) ) ).runToFuture.transformWith {
                case Success( Left( e ) ) => Future.failed( e )
                case Success( Right( user ) ) => Future.successful( user )
                case Failure( e ) => Future.failed( e )
            }
        } yield user
    }

    def addUserToGroup( userId : UUID, groupId : UUID ) : Future[ Unit ] = {
        groupService.addUserToGroup( userId = userId, groupId = groupId ).runToFuture.transformWith {
            case Success( Left( e ) ) => Future.failed( e )
            case Success( Right( group ) ) => Future.successful( group )
            case Failure( e ) => Future.failed( e )
        }
    }

    def removeUserFromGroup( userId : UUID, groupId : UUID ) : Future[ Unit ] = {
        groupService.removeUserFromGroup( userId = userId, groupId = groupId ).runToFuture.transformWith {
            case Success( Left( e ) ) => Future.failed( e )
            case Success( Right( group ) ) => Future.successful( group )
            case Failure( e ) => Future.failed( e )
        }
    }

    def deleteUser( userId : UUID ) : Future[ Unit ] = {
        userService.delete( userId = userId ).runToFuture.transformWith {
            case Success( Left( e ) ) => Future.failed( e )
            case Success( Right( user ) ) => Future.successful( user )
            case Failure( e ) => Future.failed( e )
        }
    }

    def getAllUsers : Future[ List[ User ] ] = {
        userService.fetchL().runToFuture.transformWith {
            case Failure( e ) => Future.failed( e )
            case Success( users ) => Future.successful( users.toList )
        }
    }

    def getUser( userName : String ) : Future[ User ] = {
        userService.fetchL( username = Some( userName ) ).runToFuture.transformWith {
            case Failure( e ) => Future.failed( e )
            case Success( user ) => Future.successful( user.head )
        }
    }

    def getGroupsFromUser( userId : UUID ) : Future[ List[ Group ] ] = {
        userService.fetchGroupsL( userId ).runToFuture.transformWith {
            case Failure( e ) => Future.failed( e )
            case Success( group ) => Future.successful( group.toList )
        }
    }

    def getAllUsersFromGroup( groupId : UUID ) : Future[ List[ User ] ] = {
        groupService.fetchUsers( groupId = groupId ).runToFuture.transformWith {
            case Success( Left( e ) ) => Future.failed( e )
            case Success( Right( group ) ) => Future.successful( group )
            case Failure( e ) => Future.failed( e )
        }
    }
}
