package com.twosixlabs.dart.auth.tenant.indices

import com.twosixlabs.dart.arangodb.tables.TenantDocsTables
import com.twosixlabs.dart.arangodb.{Arango, ArangoConf}
import com.twosixlabs.dart.auth.tenant.CorpusTenantIndex.{DocIdAlreadyInTenantException, DocIdMissingFromIndexException, DocIdMissingFromTenantException, InvalidTenantIdException, TenantAlreadyExistsException, TenantNotFoundException}
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, CorpusTenantIndex}
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class ArangoCorpusTenantIndex( dependencies : ArangoCorpusTenantIndex.Dependencies ) extends CorpusTenantIndex {
    import dependencies._

    override implicit val executionContext : ExecutionContext = scala.concurrent.ExecutionContext.global

    override def allTenants : Future[ Seq[ CorpusTenant ] ] = tenantDocTable
      .getTenants.map( _.toSeq.map( CorpusTenant( _ ) ) )

    override def tenant( tenantId : TenantId ) : Future[ CorpusTenant ] = {
        tenantId match {
            case ValidTenant( id ) =>
                tenantDocTable
                  .getTenant( id ).map( _.map( CorpusTenant( _ ) ).getOrElse( throw new TenantNotFoundException( tenantId ) ) )
            case invalidId => Future.failed( new InvalidTenantIdException( invalidId ) )
        }

    }

    override def tenantDocuments( tenantId : TenantId ) : Future[ Seq[ DocId ] ] = tenant( tenantId )
      .flatMap( _ => tenantDocTable.getDocsByTenant( tenantId ).map(_.toSeq) )

    override def documentTenants( docId : DocId ) : Future[ Seq[ CorpusTenant ] ] = tenantDocTable
      .getTenantsByDoc( docId ).map( _.toSeq.map( CorpusTenant( _ ) ) )

    override def addTenants( tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = {
        Future.sequence( tenantIds.map( tId => tenant( tId ).transformWith {
            case Success( _ ) => Future.failed( new TenantAlreadyExistsException( tId ) )
            case Failure( e : InvalidTenantIdException ) => Future.failed( e )
            case Failure( _ : TenantNotFoundException ) => Future.successful( tId )
            case Failure( e ) => Future.failed( new Exception( s"Unable to check existence of ${tId}", e ) )
        } ) )
          .flatMap { tIds =>
              Future.sequence( tIds.map( tenantDocTable.addTenant) ).map( _ => () )
          }
    }

    override def removeTenants( tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = {
        Future.sequence( tenantIds.map( tId => tenant( tId ).transformWith {
            case Success( _ ) => Future.successful( tId )
            case Failure( e : InvalidTenantIdException ) => Future.failed( e )
            case Failure( e : TenantNotFoundException ) => Future.failed( e )
            case Failure( e ) => Future.failed( new Exception( s"Unable to check existence of ${tId}", e ) )
        } ) )
          .flatMap { tIds =>
              Future.sequence( tIds.map( tenantDocTable.removeTenant) ).map( _ => () )
          }
    }

    override def addDocumentsToTenants( docIds : Iterable[ DocId ],
                                        tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = {
        Future.sequence { tenantIds
          .map( ( v : TenantId ) => (v, tenantDocuments( v ) ) )
          .map( ( tup : (TenantId, Future[ Seq[ DocId ] ]) ) => {
              val (tId, dIdsFut) = tup
              dIdsFut transform {
                  case Success( dIds ) =>
                      dIds.find( v => docIds.toSet.contains( v ) ) match {
                          case Some( d ) => Failure( new DocIdAlreadyInTenantException( d, tId ) )
                          case None => Success( tId )
                      }
                  case Failure( e ) => Failure( e )
              }
          } ) }
          .flatMap( ( tIds : Iterable[ TenantId ] ) => {
              Future.sequence( for {
                  tId <- tIds
                  docId <- docIds
              } yield tenantDocTable.addDocToTenant( tId, docId ) )
                .map( _ => () )
          } )
    }

    override def removeDocumentsFromTenants( docIds : Iterable[ DocId ],
                                             tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = {
        Future.sequence { tenantIds
          .map( ( tId : TenantId ) => {
              tenantDocuments( tId ) transform {
                  case Success( dIds ) =>
                      docIds.find( v => !dIds.toSet.contains( v ) ) match {
                          case Some( d ) => Failure( new DocIdMissingFromTenantException( tId, d ) )
                          case None => Success( tId )
                      }
                  case Failure( e ) => Failure( e )
              }
          } ) }
          .flatMap( ( tIds : Iterable[ TenantId ] ) => {
              Future.sequence( for {
                  tId <- tIds
                  docId <- docIds
              } yield tenantDocTable.removeDocFromTenant( tId, docId ) )
                .map( _ => () )
          } )
    }

    override def removeDocumentsFromIndex( docIds : Iterable[ DocId ] ) : Future[ Unit ] = {
        allTenants.flatMap( tenantIds => {
            Future.sequence( tenantIds.map( tenantDocuments ) )
              .map( _.zip( tenantIds ) )
              .map( _.foldLeft( Map.empty[ TenantId, Seq[ DocId ] ] )( (m : Map[ TenantId, Seq[ DocId ] ], tup : (Seq[ DocId ], CorpusTenant )) => {
                  val (dIds, t) = tup
                  m + (t.id -> dIds)
              } ) ) transformWith {
                case Success( m ) =>
                    docIds.find( dId => m.forall( tup => !tup._2.contains( dId ) ) ) match {
                        case Some( dId ) => Future.failed( new DocIdMissingFromIndexException( dId ) )
                        case None => Future.sequence {
                            m.map( tup => {
                                removeDocumentsFromTenant( docIds.filter( dId => tup._2.contains( dId ) ), tup._1 )
                            } )
                        }
                    }
                case Failure( e ) => Future.failed( e )
            }
        } )
    } map ( _ => () )
}

object ArangoCorpusTenantIndex {
    trait Dependencies {
        val tenantDocTable : TenantDocsTables

        def buildArangoTenantIndex : ArangoCorpusTenantIndex = {
            new ArangoCorpusTenantIndex( this )
        }
    }

    def apply( tenantsDocsTable : TenantDocsTables ) : ArangoCorpusTenantIndex = {
        val tdt = tenantsDocsTable
        new Dependencies {
            override val tenantDocTable : TenantDocsTables = tdt
        }.buildArangoTenantIndex
    }

    def apply( arango : Arango ) : ArangoCorpusTenantIndex = {
        apply( new TenantDocsTables( arango ) )
    }

    def apply( arangoConf : ArangoConf ) : ArangoCorpusTenantIndex = {
        apply( new Arango( arangoConf ) )
    }

    def apply( config : Config ) : ArangoCorpusTenantIndex = apply {
        ArangoConf(
            config.getString( "arangodb.host" ),
            config.getInt( "arangodb.port" ),
            config.getString( "arangodb.database" )
            )
    }
}
