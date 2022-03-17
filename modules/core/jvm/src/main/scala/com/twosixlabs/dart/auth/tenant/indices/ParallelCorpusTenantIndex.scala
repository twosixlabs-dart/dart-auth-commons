package com.twosixlabs.dart.auth.tenant.indices

import com.twosixlabs.dart.auth.tenant.CorpusTenantIndex.NonAtomicTenantIndexFailureException
import com.twosixlabs.dart.auth.tenant.indices.ParallelCorpusTenantIndex.NonAtomicParallelIndexFailureException
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, CorpusTenantIndex}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Sync multiple tenant indices at the same time. Only reads from a primary index,
 * but updates all indices. Atomic syncing.
 *
 * @param primaryIndex [[CorpusTenantIndex]]: Index to be used for retrieving data
 * @param secondaryIndices [[CorpusTenantIndex]]*: Indices to be updated but not read
 */
class ParallelCorpusTenantIndex( primaryIndex : CorpusTenantIndex, secondaryIndices : CorpusTenantIndex* ) extends CorpusTenantIndex {
    val allIndices = primaryIndex +: secondaryIndices

    override implicit val executionContext : ExecutionContext = primaryIndex.executionContext

    override def allTenants : Future[ Seq[ CorpusTenant ] ] = primaryIndex.allTenants

    override def tenant( tenantId : TenantId ) : Future[ CorpusTenant ] = primaryIndex.tenant( tenantId )

    override def tenantDocuments( tenantId : TenantId ) : Future[ Seq[ DocId ] ] = primaryIndex.tenantDocuments( tenantId )

    override def documentTenants( docId : DocId ) : Future[ Seq[ CorpusTenant ] ] = primaryIndex.documentTenants( docId )

    private implicit class AtomicFutures[ T ]( futures : Seq[ Future[ T ] ] )( implicit operation : String ) {
        def withAtomicRecovery( fn : (CorpusTenantIndex, T) => Future[ Unit ] ) : Future[ Unit ] = {
            Future.sequence( futures.map( _ transform {
                case Success( res ) => Success( Right[ Throwable, T ]( res ) )
                case Failure( e ) => Success( Left[ Throwable, T ]( e ) )
            } ) ) flatMap { results =>
                if ( results.forall( _.isRight ) ) Future.successful()
                else if ( results.forall( _.isLeft ) ) Future.failed( results.head.left.get )
                else {
                    val originalFailure : Throwable = results.find( _.isLeft ).map( _.left.get ).get
                    Future.sequence {
                        results
                          .zipWithIndex
                          .filter( _._1.isRight )
                          .map( t => (t._1.right.get, allIndices( t._2 )) )
                          .map( t => fn( t._2, t._1 ) )
                    } transformWith {
                        case Success( _ ) => Future.failed( originalFailure )
                        case Failure( recoveryFailure ) =>
                            Future.failed( new NonAtomicParallelIndexFailureException( originalFailure, recoveryFailure, operation ) )
                    }
                }
            }
        }
    }

    override def addTenants( tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = {
        implicit val operation : String = "tenant creation"
        allIndices.map( _.addTenants( tenantIds ) ) withAtomicRecovery { ( index, _ ) => {
            index.removeTenants( tenantIds )
        } }
    }

    override def removeTenants( tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = {
        implicit val operation : String = "tenant removal"
        allIndices.map( _.removeTenants( tenantIds ) ) withAtomicRecovery { ( index, _ ) => {
            index.addTenants( tenantIds )
        } }
    }

    override def addDocumentsToTenants( docIds : Iterable[ DocId ],
                                        tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = {
        implicit val operation : String = "document indexing"
        allIndices.map( _.addDocumentsToTenants( docIds, tenantIds ) ) withAtomicRecovery { ( index, _ ) => {
            index.removeDocumentsFromTenants( docIds, tenantIds )
        } }
    }

    override def removeDocumentsFromTenants( docIds : Iterable[ DocId ],
                                             tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = {
        implicit val operation : String = "document removal"
        allIndices.map( _.removeDocumentsFromTenants( docIds, tenantIds ) ) withAtomicRecovery { ( index, _ ) => {
            index.addDocumentsToTenants( docIds, tenantIds )
        } }
    }

    override def removeDocumentsFromIndex( docIds : Iterable[ DocId ] ) : Future[ Unit ] = {
        implicit val operation : String = "document removal"
        Future.sequence( docIds.map( dId => documentTenants( dId ).map( tIds => (dId, tIds ) ) ) )
          .flatMap( docMap => {
              allIndices.map( _.removeDocumentsFromIndex( docIds ) ) withAtomicRecovery { ( index, _ ) => {
                  Future.sequence( docMap.map( tup => {
                      val (dId, tIds) = tup
                      index.addDocumentToTenants( dId, tIds.map( _.id ) )
                  } ) ) map ( _ => () )
              } }
          } )

    }

    override def cloneTenant( existingTenant : TenantId,
                              newTenant : TenantId ) : Future[ Unit ] = {
        implicit val operation : String = "tenant clone"
        for {
            confirmedTenant <- tenant( existingTenant )
            _ <- allIndices.map( _.cloneTenant( confirmedTenant.id, newTenant ) ) withAtomicRecovery { ( index, _ ) =>
                index.removeTenant( newTenant )
            }
        } yield ()
    }
}

object ParallelCorpusTenantIndex {
    class NonAtomicParallelIndexFailureException( originalException : Throwable, recoveryFailure : Throwable, operation: String )
      extends NonAtomicTenantIndexFailureException( originalException, recoveryFailure, s"Parallel index $operation operation" )
}
