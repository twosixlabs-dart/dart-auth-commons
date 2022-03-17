package com.twosixlabs.dart.auth.tenant

import com.twosixlabs.cdr4s.core.CdrDocument
import com.twosixlabs.dart.auth.tenant.CorpusTenantIndex.NonAtomicTenantIndexFailureException
import com.twosixlabs.dart.auth.utilities.AsyncUtils
import com.twosixlabs.dart.exceptions.ExceptionImplicits.FutureExceptionLogging

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex
import scala.util.{Failure, Success}

trait CorpusTenantIndex {

    type DocId = CorpusTenantIndex.DocId
    type TenantId = CorpusTenantIndex.TenantId

    implicit val executionContext : ExecutionContext

    final val ValidTenant : Regex = CorpusTenantIndex.ValidTenant

    // Read

    def allTenants : Future[ Seq[ CorpusTenant ] ]

    def tenant( tenantId : TenantId ) : Future[ CorpusTenant ]

    def tenantDocuments( tenantId : TenantId ) : Future[ Seq[ DocId ] ]
    def tenantDocuments( corpusTenant: CorpusTenant ) : Future[ Seq[ DocId ] ] = tenantDocuments( corpusTenant.id )

    def documentTenants( docId : DocId ) : Future[ Seq[ CorpusTenant ] ]
    def documentTenants( cdrDocument : CdrDocument ) : Future[ Seq[ CorpusTenant ] ] = documentTenants( cdrDocument.documentId )

    // Write

    def addTenants( tenantIds : Iterable[ TenantId ] ) : Future[ Unit ]
    def addTenant( tenant : CorpusTenant, otherTenants : CorpusTenant* ) : Future[ Unit ] = addTenants( ( tenant +: otherTenants ).map( _.id ) )
    def addTenant( tenantId : TenantId, otherTenantIds : TenantId* ) : Future[ Unit ] = addTenants( tenantId +: otherTenantIds )

    def removeTenants( tenantIds : Iterable[ TenantId ] ) : Future[ Unit ]
    def removeTenant( tenant : CorpusTenant, otherTenants : CorpusTenant* ) : Future[ Unit ] = removeTenants( ( tenant +: otherTenants ).map( _.id ) )
    def removeTenant( tenantId : TenantId, otherTenantIds : TenantId* ) : Future[ Unit ] = removeTenants( tenantId +: otherTenantIds )

    def addDocumentsToTenants( docIds : Iterable[ DocId ], tenantIds : Iterable[ TenantId ] ) : Future[ Unit ]

    def addDocumentToTenants( docId : DocId, tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = addDocumentsToTenants( Seq( docId ), tenantIds )
    def addDocumentToTenant( docId : DocId, tenantId : TenantId, otherTenantIds : TenantId* ) : Future[ Unit ] = addDocumentToTenants( docId, tenantId +: otherTenantIds )
    def addDocumentToTenant( doc : CdrDocument, tenantId : TenantId, otherTenantIds : TenantId* ) : Future[ Unit ] = addDocumentToTenants( doc.documentId, tenantId +: otherTenantIds )
    def addDocumentToTenant( docId : DocId, tenant : CorpusTenant, otherTenants : CorpusTenant* ) : Future[ Unit ] = addDocumentToTenants( docId, tenant.id +: otherTenants.map( _.id ) )
    def addDocumentToTenant( doc : CdrDocument, tenant : CorpusTenant, otherTenants : CorpusTenant* ) : Future[ Unit ] = addDocumentToTenants( doc.documentId, tenant.id +: otherTenants.map( _.id ) )

    def addDocumentsToTenant( docIds : Iterable[ DocId ], tenantId : TenantId ) : Future[ Unit ] = addDocumentsToTenants( docIds, Seq( tenantId ) )

    def removeDocumentsFromTenants( docIds : Iterable[ DocId ], tenantIds : Iterable[ TenantId ] ) : Future[ Unit ]

    def removeDocumentFromTenants( docId : DocId, tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = removeDocumentsFromTenants( Seq( docId ), tenantIds )
    def removeDocumentFromTenant( docId : DocId, tenantId : TenantId, otherTenantIds : TenantId* ) : Future[ Unit ] = removeDocumentFromTenants( docId, tenantId +: otherTenantIds )
    def removeDocumentFromTenant( doc : CdrDocument, tenantId : TenantId, otherTenantIds : TenantId* ) : Future[ Unit ] = removeDocumentFromTenants( doc.documentId, tenantId +: otherTenantIds )
    def removeDocumentFromTenant( docId : DocId, tenant : CorpusTenant, otherTenants : CorpusTenant* ) : Future[ Unit ] = removeDocumentFromTenants( docId, tenant.id +: otherTenants.map( _.id ) )
    def removeDocumentFromTenant( doc : CdrDocument, tenant : CorpusTenant, otherTenants : CorpusTenant* ) : Future[ Unit ] = removeDocumentFromTenants( doc.documentId, tenant.id +: otherTenants.map( _.id ) )

    def removeDocumentsFromTenant( docIds : Iterable[ DocId ], tenantId : TenantId ) : Future[ Unit ] = removeDocumentsFromTenants( docIds, Seq( tenantId ) )

    def removeDocumentsFromIndex( docIds : Iterable[ DocId ] ) : Future[ Unit ]
    def removeDocumentFromIndex( docId: DocId, otherDocIds : DocId* ) : Future[ Unit ] = removeDocumentsFromIndex( docId +: otherDocIds )
    def removeDocumentFromIndex( doc: CdrDocument, otherDocs : CdrDocument* ) : Future[ Unit ] = removeDocumentsFromIndex( doc.documentId +: otherDocs.map( _.documentId ) )

    def cloneTenant( existingTenant : TenantId, newTenant : TenantId ) : Future[ Unit ] = {
        for {
            confirmedExistingTenant <- tenant( existingTenant )
            docIds <- tenantDocuments( confirmedExistingTenant )
            _ <- addTenant( newTenant )
            addedTenant <- AsyncUtils.retry( () => tenant( newTenant ), times = 10, delay = 1.seconds )
            _ <- addDocumentsToTenants( docIds, Some( addedTenant.id ) ) transformWith {
                case Success( _ ) => Future.successful()
                case Failure( e ) =>
                    removeTenants( Some( addedTenant.id ) ) transform {
                        case Success( _ ) => Failure( e )
                        case Failure( e2 ) => Failure( new NonAtomicTenantIndexFailureException( e, e2, "tenant clone" ) )
                    }
            }
        } yield ()
    }
    final def cloneTenant( existingTenant : CorpusTenant, newTenant : TenantId ) : Future[ Unit ] = cloneTenant( existingTenant.id, newTenant )

    // Class Extensions

    implicit class CdrDocumentWithTenants( cdrDocument : CdrDocument ) {

        def tenants : Future[ Seq[ CorpusTenant ] ] = documentTenants( cdrDocument )

        def addToTenant( tenantId : TenantId, otherTenantIds : TenantId* ) : Future[ Unit ] = addDocumentToTenant( cdrDocument.documentId, tenantId, otherTenantIds : _* )
        def addToTenant( tenant : CorpusTenant, otherTenants : CorpusTenant* ) : Future[ Unit ] = addDocumentToTenant( cdrDocument, tenant, otherTenants : _* )
        def addToTenants( tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = addDocumentToTenants( cdrDocument.documentId, tenantIds )

        def removeFromTenant( tenantId : TenantId, otherTenantIds : TenantId* ) : Future[ Unit ] = removeDocumentFromTenant( cdrDocument, tenantId, otherTenantIds : _* )
        def removeFromTenant( tenant : CorpusTenant, otherTenants : CorpusTenant* ) : Future[ Unit ] = removeDocumentFromTenant( cdrDocument, tenant, otherTenants : _* )
        def removeFromTenants( tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = removeDocumentFromTenants( cdrDocument.documentId, tenantIds )

        def removeFromIndex() : Future[ Unit ] = removeDocumentFromIndex( cdrDocument )

    }

    implicit class CorpusTenantWithDocuments( corpusTenant : CorpusTenant ) {

        def addToIndex() : Future[ Unit ] = addTenant( corpusTenant )

        def removeFromIndex() : Future[ Unit ] = removeTenant( corpusTenant )

        def documents : Future[ Seq[ DocId ] ] = tenantDocuments( corpusTenant )

        def addDocument( docId : DocId, otherDocIds : DocId* ) : Future[ Unit ] = addDocumentsToTenant( docId +: otherDocIds, corpusTenant.id )
        def addDocument( doc : CdrDocument, otherDocs : CdrDocument* ) : Future[ Unit ] = addDocumentsToTenant( doc.documentId +: otherDocs.map( _.documentId ), corpusTenant.id )
        def addDocuments( docIds : Iterable[ DocId ] ) : Future[ Unit ] = addDocumentsToTenant( docIds, corpusTenant.id )

        def removeDocument( docId : DocId, otherDocIds : DocId* ) : Future[ Unit ] = removeDocumentsFromTenant( docId +: otherDocIds, corpusTenant.id )
        def removeDocument( cdrDocument : CdrDocument, otherCdrDocs : CdrDocument* ) : Future[ Unit ] = removeDocumentsFromTenant( cdrDocument.documentId +: otherCdrDocs.map( _.documentId ), corpusTenant.id )
        def removeDocuments( docIds : Iterable[ DocId ] ) : Future[ Unit ] = removeDocumentsFromTenant( docIds, corpusTenant.id )

        def clone( newId : TenantId ) : Future[ Unit ] = cloneTenant( corpusTenant, newId )

    }

}

object CorpusTenantIndex {

    type DocId = String
    type TenantId = String

    val ValidTenant : Regex = """^([a-z0-9\-]+)$""".r

    class CorpusTenantIndexException( message : String, cause : Option[ Throwable ] = None ) extends Exception( message, cause.orNull )

    class TenantNotFoundException( val id : TenantId ) extends CorpusTenantIndexException( s"Tenant does not exist with id: $id" )
    class TenantAlreadyExistsException( val id : TenantId ) extends CorpusTenantIndexException( s"Tenant already exists with id: $id" )
    class InvalidTenantIdException( val id : TenantId ) extends CorpusTenantIndexException( s"Tenant id ${id} is not valid. Acceptable format: ${ValidTenant.toString}." )
    class DocIdMissingFromTenantException( val tenantId : TenantId, val docId : DocId ) extends CorpusTenantIndexException( s"Document $docId is not in tenant $tenantId" )
    class DocIdMissingFromIndexException( val docId : DocId ) extends CorpusTenantIndexException( s"Document $docId is not in any tenants" )
    class DocIdAlreadyInTenantException( val docId : DocId, val tenantId : TenantId ) extends CorpusTenantIndexException( s"Document $docId is already in tenant $tenantId" )
    class NonAtomicTenantIndexFailureException( val originalException : Throwable, val recoveryFailure : Throwable, operation : String )
      extends CorpusTenantIndexException( s"Unable to rollback changes in attempt to enforce atomic operation: $operation\nRecovery failure: ${recoveryFailure.getMessage}", Some( originalException ) )
}
