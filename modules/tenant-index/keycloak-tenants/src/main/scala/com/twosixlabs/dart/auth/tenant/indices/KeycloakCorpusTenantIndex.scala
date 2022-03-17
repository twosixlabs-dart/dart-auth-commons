package com.twosixlabs.dart.auth.tenant.indices

import com.fullfacing.keycloak4s.core.models.Group
import com.twosixlabs.dart.auth.keycloak.KeycloakAdminClient
import com.twosixlabs.dart.auth.tenant.CorpusTenantIndex.{InvalidTenantIdException, TenantAlreadyExistsException, TenantNotFoundException}
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, CorpusTenantIndex}
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class KeycloakCorpusTenantIndex( dependencies : KeycloakCorpusTenantIndex.Dependencies ) extends CorpusTenantIndex {
    import dependencies._

    override implicit val executionContext : ExecutionContext = scala.concurrent.ExecutionContext.global

    override def allTenants : Future[ Seq[ CorpusTenant ] ] = keycloakClient
      .getAllGroups
      .map( _.flatMap { grp : Group =>
          Try( CorpusTenant( grp.name ) ).toOption
      } )

    override def tenant( tenantId : TenantId ) : Future[ CorpusTenant ] = keycloakClient
      .getGroup( tenantId )
      .map { groupOption : Option[ Group ] =>
          groupOption
            .map( grp => CorpusTenant( grp.name ) )
            .getOrElse( throw new TenantNotFoundException( tenantId ) )
      }

    override def tenantDocuments( tenantId : TenantId ) : Future[ Seq[ DocId ] ] = ???

    override def documentTenants( docId : DocId ) : Future[ Seq[ CorpusTenant ] ] = ???

    override def addTenants( tenants : Iterable[ TenantId ] ) : Future[ Unit ] = {
        tenants.find {
            case ValidTenant( _ ) => false
            case _ => true
        } match {
            case Some( invalidTenantId ) => Future.failed( new InvalidTenantIdException( invalidTenantId ) )
            case None =>
                for {
                    existingTenants <- allTenants
                    _ <- tenants.find( t => existingTenants.map( _.id ).contains( t ) ) match {
                        case Some( existingTenant ) => Future.failed( new TenantAlreadyExistsException( existingTenant ) )
                        case None => Future.sequence( tenants map { tenant =>
                            keycloakClient.createGroupAndRetrieve( tenant, List( "leader", "member", "read-only" ) )
                              .map( _ => () )
                        } )
                    }
                } yield ()
        }
    }

    override def removeTenants( tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = {
        Future.sequence( tenantIds.map( tenant ) ).flatMap( tenants => {
            Future.sequence( tenants.map( tenant => {
                val tenantId = tenant.id
                for {
                    groupOption <- keycloakClient.getGroup( tenantId )
                    group <- Future( groupOption.getOrElse( throw new TenantNotFoundException( tenantId ) ) )
                    _ <- keycloakClient.deleteGroup( group.id )
                } yield ()
            } ) )
        } ) map ( _ => () )
    }

    override def addDocumentsToTenants( docIds : Iterable[ DocId ],
                                        tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = Future.successful()

    override def removeDocumentsFromTenants( docIds : Iterable[ DocId ],
                                             tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = Future.successful()

    override def removeDocumentsFromIndex( docIds : Iterable[ DocId ] ) : Future[ Unit ] = Future.successful()

    override def cloneTenant( existingTenant : TenantId,
                              newTenant : TenantId ) : Future[ Unit ] = {
        addTenant( newTenant )
    }
}

object KeycloakCorpusTenantIndex {
    trait Dependencies {
        val keycloakClient : KeycloakAdminClient

        def buildKeycloakTenantIndex : KeycloakCorpusTenantIndex = new KeycloakCorpusTenantIndex( this )
    }

    def apply( keycloakClient : KeycloakAdminClient ) : KeycloakCorpusTenantIndex = {
        val kc = keycloakClient
        new Dependencies {
            override val keycloakClient : KeycloakAdminClient = kc
        } buildKeycloakTenantIndex
    }

    def apply( config : Config ) : KeycloakCorpusTenantIndex = apply {
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
