package com.twosixlabs.dart.auth.user.stores

import com.fullfacing.keycloak4s.core.models.{Group, User}
import com.twosixlabs.dart.auth.groups.{DartGroup, ProgramManager, TenantGroup}
import com.twosixlabs.dart.auth.keycloak.KeycloakAdminClient
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, GlobalCorpus, Leader, Member, ReadOnly}
import com.twosixlabs.dart.auth.user.DartUserStore.{InvalidUserNameException, UserAlreadyExistsException, UserNotFoundException, ValidUser}
import com.twosixlabs.dart.auth.user.{DartUser, DartUserInfo, DartUserStore, UserMod}
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

class KeycloakUserStore( dependencies : KeycloakUserStore.Dependencies ) extends DartUserStore {

    import dependencies._
    val LOG : Logger = LoggerFactory.getLogger( getClass )
    override implicit val ec : ExecutionContext = scala.concurrent.ExecutionContext.global

    private def getUserGroupFromPath( path : String ) : Option[ DartGroup ] = {
        val ProgramManagerPattern : Regex = """/program-manager""".r
        val GroupPattern : Regex = """/([a-z\-]+)\/([a-z\-]+)""".r
        path match {
            case ProgramManagerPattern() => Some( ProgramManager )
            case GroupPattern( tenantStr, roleStr ) =>
                val tenant = tenantStr match {
                    case "global" => GlobalCorpus
                    case _ => CorpusTenant( tenantStr, GlobalCorpus )
                }
                roleStr match {
                    case "leader" => Some( TenantGroup( tenant, Leader ) )
                    case "member" => Some( TenantGroup( tenant, Member ) )
                    case "read-only" => Some( TenantGroup( tenant, ReadOnly ) )
                    case _ => None
                }
            case _ => None
        }
    }

    private def getGroupName( grp : DartGroup ) : (String, Option[ String ]) = grp match {
        case ProgramManager => ("program-manager", None)
        case tg@TenantGroup( tenant, tenantRole ) =>
            (tg.tenantSlug, Some( tenantRole.toString ))
    }

    private def getGroupFromDartGroup( grp : DartGroup ) : Future[ Group ] = {
        getGroupName( grp ) match {
            case (name, None) => keycloakClient.getGroup( name ).map( _.get )
            case (name, Some(subName)) => keycloakClient.getGroup( name )
                .map( _.get.subGroups.find( _.name == subName ).get )
        }
    }

    private def addGroupsToUser( dartUser : User, groups : Set[ DartGroup ] ) : Future[ Unit ] = {
        Future.sequence( groups.map( ( grp : DartGroup ) => for {
            keycloakUser <- keycloakClient.getUser( dartUser.username )
            _ <- getGroupFromDartGroup( grp ).flatMap( g => keycloakClient.addUserToGroup( keycloakUser.id, g.id ) )
        } yield () ) ).map( _ => () )
    }

    override def allUsers : Future[ Set[ DartUser ] ] = {
        keycloakClient.getAllUsers.flatMap( ( users : Seq[ User ] ) => {
            Future.sequence( users.map( ( user : User ) => {
                keycloakClient.getGroupsFromUser( user.id ).map( groups => {
                    groups.flatMap( group => {
                        getUserGroupFromPath( group.path )
                    } )
                } ).map( dartGroups => {
                    DartUser( user.username, dartGroups.toSet, DartUserInfo(user.firstName,user.lastName, user.email) )
                } )
            } ).toSet )
        } )
    }

    override def user( name : String ) : Future[ DartUser ] = {
        keycloakClient.getUser( name ).flatMap( ( user : User ) => {
            keycloakClient.getGroupsFromUser( user.id ).map( groups => {
                groups.flatMap( group => getUserGroupFromPath( group.path ) )
            } ).map( dartGroups => DartUser( user.username, dartGroups.toSet, DartUserInfo(user.firstName,user.lastName, user.email) ) )
        } ) transform {
            case Failure( e : NoSuchElementException ) => Failure( new UserNotFoundException( name ) )
            case other => other
        }
    }

    override def addUsers( users : Seq[ UserMod ] ) : Future[ Unit ] = {
        //TODO: check get group for keycloak to return one group
        for {
            validUsers <- Future( users.map {
                case UserMod( usr@DartUser( ValidUser( _ ), _, _), _ ) => usr
                case UserMod( DartUser( invalidUserName, _, _ ), _ ) => throw new InvalidUserNameException( invalidUserName )
            } )
            _ <- allUsers transform {
                case Success( usrs ) =>
                    usrs.find( usr => validUsers.contains( usr ) ) match {
                        case Some( usr ) => Failure( new UserAlreadyExistsException( usr.userName ) )
                        case None => Success()
                    }
                case fl@Failure( _ ) => fl
            }
            _ <- Future.sequence {
                users.map( userMod => {
                    val user = userMod.user
                    keycloakClient.createUserAndRetrieve( user.userName, user.userInfo.firstName, user.userInfo.lastName, user.userInfo.email, userMod.password ).flatMap( dartUser => {
                        addGroupsToUser(dartUser, user.groups)
                    } )
                } )
            }
        } yield ()
    }

    override def removeUsers( names : Seq[ String ] ) : Future[ Unit ] = {
        for {
            _ <- allUsers transform {
                case Success( usrs ) =>
                    names.find( name => !usrs.map( _.userName ).contains( name ) ) match {
                        case Some( missingName ) => Failure( new UserNotFoundException( missingName ) )
                        case None => Success( () )
                    }
                case fl@Failure( _ ) => fl
            }
            _ <- Future.sequence {
                names.map( user => {
                    for {
                        user <- keycloakClient.getUser( user )
                        _ <- keycloakClient.deleteUser( user.id )
                    } yield ()
                } )
            }
        } yield ()
    }

    override def updateUsers( users : Seq[ UserMod ] ) : Future[ Unit ] = {
        for {
            originalUsersMap <- ( allUsers transform ( ( usrsTry : Try[ Set[ DartUser ] ] ) => usrsTry match {
                case Success( usersInKeycloak ) =>
                    users.map( _.user.userName ).find( userInDartName => !usersInKeycloak.map( _.userName ).contains( userInDartName ) ) match {
                        case Some( missingName ) => Failure( new UserNotFoundException( missingName ) )
                        case None => Success( usersInKeycloak.toList.map( ( u : DartUser ) => ( u.userName -> u) ).toMap )
                    }
                case Failure( e ) => Failure( e )
            } ) )
            _ <- Future.sequence {
                users.map( userMod => {
                    val user = userMod.user
                    val passwordOpt = userMod.password
                    for {
                        userId <- keycloakClient.updateUser(user.userName, user.userInfo.firstName, user.userInfo.lastName, user.userInfo.email, passwordOpt)
                          .map( _.id )
                        (extraGroups, missingGroups) <- {
                            val originalGroups : Set[ DartGroup ] = originalUsersMap( user.userName ).groups
                            val extraGroups : Set[ DartGroup ] = originalGroups.diff( user.groups )
                            val missingGroups = user.groups.diff( originalGroups )
                            Future( (extraGroups, missingGroups) )
                        }
                        _ <- Future.sequence( extraGroups.map( group => for {
                            groupId <- getGroupFromDartGroup( group ).map( _.id )
                            _ <- keycloakClient.removeUserFromGroup( userId, groupId )
                        } yield () ) )
                        _ <- Future.sequence( missingGroups.map( group => for {
                            groupId <- getGroupFromDartGroup( group ).map( _.id )
                            _ <- keycloakClient.addUserToGroup( userId, groupId )
                        } yield () ) )
                    } yield ()
                } )
            }
        } yield ()
    }
}

object KeycloakUserStore {
    trait Dependencies {
        val keycloakClient : KeycloakAdminClient

        def buildKeycloakUserStore : KeycloakUserStore = new KeycloakUserStore( this )
    }

    def apply( keycloakClient : KeycloakAdminClient ) : KeycloakUserStore = {
        val kc = keycloakClient
        new Dependencies {
            override val keycloakClient : KeycloakAdminClient = kc
        } buildKeycloakUserStore
    }

    def apply( config : Config ) : KeycloakUserStore = apply {
        KeycloakAdminClient(
            Try( config.getString( "keycloak.scheme" ) ).getOrElse( "http" ),
            config.getString( "keycloak.host" ),
            config.getInt( "keycloak.port" ),
            List( Try( config.getString( "keycloak.base.path" ) ).getOrElse( "auth" ) ),
            config.getString( "keycloak.admin.realm" ),
            config.getString( "keycloak.admin.client.id" ),
            config.getString( "keycloak.admin.client.secret" ),
            config.getString( "keycloak.realm" ),
        )
    }
}
