package com.twosixlabs.dart.auth.tenant.indices

import com.twosixlabs.dart.auth.tenant.CorpusTenantIndex.{DocIdAlreadyInTenantException, DocIdMissingFromIndexException, DocIdMissingFromTenantException, InvalidTenantIdException, TenantAlreadyExistsException, TenantNotFoundException}
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, CorpusTenantIndex}
import com.twosixlabs.dart.cassandra.{Cassandra, CassandraConf}
import com.twosixlabs.dart.cassandra.tables.TenantsDocsTables
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class CassandraCorpusTenantIndex( dependencies : CassandraCorpusTenantIndex.Dependencies ) extends CorpusTenantIndex {
    import dependencies._

    override implicit val executionContext : ExecutionContext = scala.concurrent.ExecutionContext.global

    override def allTenants : Future[ Seq[ CorpusTenant ] ] = tenantDocTable
      .getTenants.map( _.map( CorpusTenant( _ ) ) )

    override def tenant( tenantId : TenantId ) : Future[ CorpusTenant ] = {
        tenantId match {
            case ValidTenant( id ) =>
                tenantDocTable
                  .getTenant( id ).map( _.map( CorpusTenant( _ ) ).getOrElse( throw new TenantNotFoundException( tenantId ) ) )
            case invalidId => Future.failed( new InvalidTenantIdException( invalidId ) )
        }

    }

    override def tenantDocuments( tenantId : TenantId ) : Future[ Seq[ DocId ] ] = tenant( tenantId )
      .flatMap( _ => tenantDocTable.getDocsByTenant( tenantId ) )

    override def documentTenants( docId : DocId ) : Future[ Seq[ CorpusTenant ] ] = tenantDocTable
      .getTenantsByDoc( docId ).map( _.map( CorpusTenant( _ ) ) )

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

object CassandraCorpusTenantIndex {
    trait Dependencies {
        val tenantDocTable : TenantsDocsTables

        def buildCassandraTenantIndex : CassandraCorpusTenantIndex = {
            new CassandraCorpusTenantIndex( this )
        }
    }

    def apply( tenantsDocsTable : TenantsDocsTables ) : CassandraCorpusTenantIndex = {
        val tdt = tenantsDocsTable
        new Dependencies {
            override val tenantDocTable : TenantsDocsTables = tdt
        }.buildCassandraTenantIndex
    }

    def apply( cassandra : Cassandra ) : CassandraCorpusTenantIndex = {
        apply( new TenantsDocsTables( cassandra ) )
    }

    def apply( cassandraConf : CassandraConf ) : CassandraCorpusTenantIndex = {
        apply( new Cassandra( cassandraConf ) )
    }

    def apply( config : Config ) : CassandraCorpusTenantIndex = apply {
        CassandraConf(
            List( config.getString( "cassandra.host" ) ),
            config.getInt( "cassandra.port" ),
            config.getLong( "cassandra.statement.cache.size" ),
            Try( config.getString( "cassandra.keyspace" ) ).toOption,
        )
    }
}
