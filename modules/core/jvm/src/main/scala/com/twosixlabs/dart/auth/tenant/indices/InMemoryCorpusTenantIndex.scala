package com.twosixlabs.dart.auth.tenant.indices

import com.twosixlabs.dart.auth.tenant.CorpusTenantIndex.{DocIdAlreadyInTenantException, DocIdMissingFromIndexException, DocIdMissingFromTenantException, InvalidTenantIdException, TenantAlreadyExistsException, TenantNotFoundException}
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, CorpusTenantIndex}

import scala.collection.concurrent
import scala.concurrent.{ExecutionContext, Future}

class InMemoryCorpusTenantIndex( startingTenants : CorpusTenant* ) extends CorpusTenantIndex {

    type ConcurrentSet[ T ] = concurrent.Map[ T, Unit ]

    def ConcurrentSet[ T ]( eles : T* ) : ConcurrentSet[ T ] = concurrent.TrieMap[ T, Unit ]( eles.map( ele => ele -> () ) : _* )

    override implicit val executionContext : ExecutionContext = scala.concurrent.ExecutionContext.global

    private val tenantIndex : concurrent.Map[ TenantId, CorpusTenant ] = concurrent.TrieMap[ TenantId, CorpusTenant ]()

    private val tenantMap : concurrent.Map[ DocId, ConcurrentSet[ TenantId ] ] = concurrent.TrieMap[ DocId, ConcurrentSet[ TenantId ] ]()

    private val docMap : concurrent.Map[ TenantId, ConcurrentSet[ DocId ] ] = concurrent.TrieMap[ TenantId, ConcurrentSet[ DocId ] ]()

    override def allTenants : Future[ Seq[ CorpusTenant ] ] = Future( tenantIndex.values.toList )

    override def tenant( tenantId : TenantId ) : Future[CorpusTenant ] = Future( tenantIndex.getOrElse( tenantId, throw new TenantNotFoundException( tenantId ) ) )

    override def tenantDocuments( tenantId : TenantId ) : Future[ Seq[ DocId ] ] = Future( docMap.getOrElse( tenantId, throw new TenantNotFoundException( tenantId ) ).keys.toList )

    override def documentTenants( docId : DocId ) : Future[ Seq[ CorpusTenant ] ] = Future( tenantMap.getOrElse( docId, ConcurrentSet() ).keys.toList.map( tenantIndex ) )

    override def addTenants( tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = {
        tenantIds.find {
            case ValidTenant( _ ) => false
            case _ => true
        } match {
            case Some( tId ) => Future.failed( new InvalidTenantIdException( tId ) )
            case None =>
                tenantIds.find( tenantIndex.contains ) match {
                    case Some( tId ) => Future.failed( new TenantAlreadyExistsException( tId ) )
                    case None => Future( tenantIds.foreach( tId => { tenantIndex( tId ) = CorpusTenant( tId ); docMap( tId ) = ConcurrentSet() } ) )
                }
        }
    }

    override def removeTenants( tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = {
        tenantIds.find( v => !tenantIndex.contains( v ) ) match {
            case Some( tId ) => Future.failed( new TenantNotFoundException( tId ) )
            case None => Future( tenantIds.foreach( tId => {
                tenantIndex -= tId
                docMap.getOrElse( tId, ConcurrentSet() ).foreach( dId => tenantMap( dId._1 ) -= tId )
                docMap -= tId
            } ) )
        }
    }

    override def addDocumentsToTenants( docIds : Iterable[ DocId ],
                                        tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = {
        tenantIds.find( v => !tenantIndex.contains( v ) ) match {
            case Some( tId ) => Future.failed( new TenantNotFoundException( tId ) )
            case None =>
                tenantIds.flatMap( tId => docIds.find( dId => docMap( tId ).contains( dId ) ).map( d => (d, tId ) ) ).headOption match {
                    case Some( (dId, tId ) ) => Future.failed( new DocIdAlreadyInTenantException( dId, tId ) )
                    case None => Future {
                        for {
                            dId <- docIds
                            tId <- tenantIds
                        } {
                            if ( docMap.contains( tId ) ) docMap( tId ) += (dId -> ())
                            else docMap( tId ) = ConcurrentSet( dId )
                            if ( tenantMap.contains( dId ) ) tenantMap( dId ) += (tId -> ())
                            else tenantMap( dId ) = ConcurrentSet( tId )
                        }
                    }
                }
        }
    }

    override def removeDocumentsFromTenants( docIds : Iterable[ DocId ],
                                             tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = {

        tenantIds.find( v => !tenantIndex.contains( v ) ) match {
            case Some( tId ) => Future.failed( new TenantNotFoundException( tId ) )
            case None =>
                tenantIds.flatMap( tId => docIds.find( dId => !docMap( tId ).contains( dId ) ).map( d => (d, tId ) ) ).headOption match {
                    case Some( (dId, tId ) ) => Future.failed( new DocIdMissingFromTenantException( dId, tId ) )
                    case None => Future {
                        for {
                            dId <- docIds
                            tId <- tenantIds
                        }  {
                            docMap( tId ) -= dId
                            tenantMap( dId ) -= tId
                        }
                    }
                }
        }
    }

    override def removeDocumentsFromIndex( docIds : Iterable[ DocId ] ) : Future[ Unit ] = {
        docIds.find( dId => tenantIndex.keySet.forall( tId => !docMap( tId ).contains( dId ) ) ) match {
            case Some( dId ) => Future.failed( new DocIdMissingFromIndexException( dId ) )
            case None => Future {
                for ( tId <- tenantIndex.keySet ) {
                    docMap( tId ) --= docIds.toSet
                }
                for ( dId <- docIds ) {
                    tenantMap -= dId
                }
            }
        }
    }
}
