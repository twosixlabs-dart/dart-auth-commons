package com.twosixlabs.dart.auth.tenant

import com.twosixlabs.dart.auth.tenant.CorpusTenantIndex.{DocIdMissingFromTenantException, InvalidTenantIdException, NonAtomicTenantIndexFailureException, TenantAlreadyExistsException, TenantNotFoundException}
import com.twosixlabs.dart.auth.tenant.indices.InMemoryCorpusTenantIndex
import com.twosixlabs.dart.exceptions.ExceptionImplicits.FutureExceptionLogging
import com.twosixlabs.dart.test.TestUtils
import org.scalatest.{Assertion, BeforeAndAfterEach}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

abstract class CorpusTenantIndexTest[ T <: CorpusTenantIndex ](
  val index : T,
  val timeOutMs : Long = 5000,
  val writeDelayMs : Long = 0
) extends AnyFlatSpecLike with BeforeAndAfterEach with Matchers {

    val className = index.getClass.getSimpleName

    implicit val ec : ExecutionContext = index.executionContext

    implicit class AwaitableFuture[ T ]( fut : Future[ T ] ) {
        def await( duration : Duration ) : T = Await.result( fut, duration )

        def await( ms : Long ) : T = await( ms.milliseconds )

        def await : T = await( timeOutMs )

        def awaitWrite( duration : Duration ) : T = {
            val res = Await.result( fut, duration )
            Thread.sleep( writeDelayMs )
            res
        }

        def awaitWrite( ms : Long ) : T = awaitWrite( ms.milliseconds )

        def awaitWrite : T = awaitWrite( timeOutMs )
    }

    import index.CorpusTenantWithDocuments

    override def beforeEach( ) : Unit = {
        super.beforeEach()

        require( Await.result( index.allTenants, 5.seconds ).isEmpty )
    }

    val docIds : Seq[ String ] = Range( 1, 21 ).map( i => s"doc-id-$i" )
    val originalTenants : Seq[ CorpusTenant ] = Range( 1, 4 ).map( i => CorpusTenant( s"tenant-id-$i", GlobalCorpus ) )

    behavior of className

    it should "add a new tenant" in {
        originalTenants.head.addToIndex().awaitWrite
        index.allTenants.await shouldBe Seq( originalTenants.head )
        index.tenant( originalTenants.head.id ).await shouldBe originalTenants.head

        index.addTenants( originalTenants.slice( 1, 3 ).map( _.id ) ).awaitWrite
        Await.result( index.allTenants.map( _.toSet ), 5.seconds ) shouldBe originalTenants.toSet
        Await.result( index.tenant( originalTenants.head.id ), 5.seconds ) shouldBe originalTenants.head
        Await.result( index.tenant( originalTenants( 1 ).id ), 5.seconds ) shouldBe originalTenants( 1 )
        Await.result( index.tenant( originalTenants( 2 ).id ), 5.seconds ) shouldBe originalTenants( 2 )
    }

    it should "add documents to a tenant" in {
        originalTenants.head.addToIndex().awaitWrite
        docIds.foreach( dId => index.documentTenants( dId ).await shouldBe Nil )
        index.addDocumentToTenant( docIds.head, originalTenants.head ).awaitWrite

        Await.result( originalTenants.head.documents, 5.seconds ) shouldBe Seq( docIds.head )
        Await.result( index.documentTenants( docIds.head ), 5.seconds ) shouldBe Seq( originalTenants.head )

        originalTenants( 1 ).addToIndex().awaitWrite
        index.addDocumentsToTenant( docIds, originalTenants( 1 ).id ).awaitWrite
        Await.result( originalTenants( 1 ).documents.map( _.toSet ), 5.seconds ) shouldBe docIds.toSet
        docIds.foreach( id => Await.result( index.documentTenants( id ), 5.seconds ).contains( originalTenants( 1 ) ) shouldBe true )

        originalTenants( 2 ).addToIndex().awaitWrite
        index.addDocumentsToTenant( docIds.slice( 4, 10 ), originalTenants( 2 ).id ).awaitWrite
        Await.result( originalTenants( 2 ).documents.map( _.toSet ), 5.seconds ) shouldBe docIds.slice( 4, 10 ).toSet
        docIds.slice( 4, 10 ).foreach { id =>
            val res = Await.result( index.documentTenants( id ), 5.seconds )
            res.contains( originalTenants( 2 ) ) shouldBe true
            res.contains( originalTenants( 1 ) ) shouldBe true
            res.contains( originalTenants.head ) shouldBe false
        }
    }

    it should "remove documents from a tenant" in {
        index.addTenant( originalTenants.head ).awaitWrite
        docIds.foreach( dId => index.documentTenants( dId ).await shouldBe Nil )
        originalTenants.head.addDocuments( docIds ).awaitWrite

        index.tenant( originalTenants.head.id ).await shouldBe originalTenants.head
        originalTenants.head.documents.await.toSet shouldBe docIds.toSet
        docIds.foreach( dId => index.documentTenants( dId ).await shouldBe Seq( originalTenants.head ) )

        index.removeDocumentFromTenant( docIds.head, originalTenants.head ).awaitWrite

        originalTenants.head.documents.await.toSet shouldBe (docIds.toSet - docIds.head)
        docIds.foreach( dId => {
            if ( dId == docIds.head ) index.documentTenants( dId ).await shouldBe Seq.empty
            else index.documentTenants( dId ).await shouldBe Seq( originalTenants.head )
        } )
    }

    it should "remove a document from all tenants" in {
        index.addTenants( originalTenants.map( _.id ) ).awaitWrite
        index.addDocumentToTenants( docIds.head, originalTenants.slice( 0, 5 ).map( _.id ) ).await
        index.addDocumentToTenants( docIds( 1 ), originalTenants.slice( 3, originalTenants.length ).map( _.id ) ).awaitWrite

        index.documentTenants( docIds.head ).await shouldBe originalTenants.slice( 0, 5 )
        index.documentTenants( docIds( 1 ) ).await shouldBe originalTenants.slice( 3, originalTenants.length )

        index.removeDocumentFromIndex( docIds.head ).awaitWrite

        index.documentTenants( docIds.head ).await shouldBe Nil
        index.documentTenants( docIds( 1 ) ).await shouldBe originalTenants.slice( 3, originalTenants.length )
    }

    it should "remove several documents from all tenants" in {
        index.addTenants( originalTenants.map( _.id ) ).awaitWrite
        index.addDocumentToTenants( docIds.head, originalTenants.take( 2 ).map( _.id ) ).await
        index.addDocumentToTenants( docIds( 1 ), originalTenants.drop( 2 ).map( _.id ) ).await
        index.addDocumentToTenants( docIds( 2 ), originalTenants.map( _.id ) ).awaitWrite

        index.documentTenants( docIds.head ).await shouldBe originalTenants.take( 2 )
        index.documentTenants( docIds( 1 ) ).await shouldBe originalTenants.drop( 2 )
        index.documentTenants( docIds( 2 ) ).await shouldBe originalTenants
        originalTenants.take( 2 ).foreach( tId => index.tenantDocuments( tId ).await should contain( docIds.head ) )
        originalTenants.drop( 2 ).foreach( tId => index.tenantDocuments( tId ).await should contain( docIds( 1 ) ) )
        originalTenants.foreach( tId => index.tenantDocuments( tId ).await should contain( docIds( 2 ) ) )

        index.removeDocumentsFromIndex( Seq( docIds.head, docIds( 1 ), docIds( 2 ) ) ).awaitWrite

        index.documentTenants( docIds.head ).await shouldBe Nil
        index.documentTenants( docIds( 1 ) ).await shouldBe Nil
        index.documentTenants( docIds( 2 ) ).await shouldBe Nil
    }

    it should "remove a tenant" in {
        index.addTenant( originalTenants.head ).awaitWrite
        index.addDocumentsToTenant( docIds, originalTenants.head.id ).awaitWrite

        index.allTenants.await shouldBe Seq( originalTenants.head )
        index.tenantDocuments( originalTenants.head ).await.toSet shouldBe docIds.toSet

        index.removeTenant( originalTenants.head ).awaitWrite

        index.allTenants.await shouldBe Nil

        // Make sure docIds are not still linked once tenant is added again
        index.addTenant( originalTenants.head ).awaitWrite
        index.tenantDocuments( originalTenants.head ).await shouldBe Nil
        docIds.foreach( dId => index.documentTenants( dId ).await shouldBe Nil )
    }

    it should "throw TenantNotFoundException when attempting to retrieve non-existing tenant by id" in {
        Try( Await.result( index.tenant( "non-existent-fake-tenant" ), 5.seconds ) ) match {
            case Failure( e : TenantNotFoundException ) => e.getMessage should include( "id: non-existent-fake-tenant" )
            case Failure( e ) => fail( s"threw the wrong error: $e" )
            case Success( res ) => fail( s"failed to fail: $res" )
        }
    }

    it should "throw TenantNotFoundException when attempting to remove non-existing tenant" in {
        index.addTenant( "tenant-id-1", "tenant-id-3" ).awaitWrite

        Try( Await.result( index.removeTenant( "non-existent-fake-tenant" ), 5.seconds ) ) match {
            case Failure( e : TenantNotFoundException ) => e.getMessage should include( "id: non-existent-fake-tenant" )
            case Failure( e ) => fail( s"threw the wrong error: $e" )
            case Success( res ) => fail( s"failed to fail: $res" )
        }

        Try( Await.result( index.removeTenant( "tenant-id-1", "non-existent-fake-tenant-1", "non-existent-fake-tenant-2", "tenant-id-3" ), 5.seconds ) ) match {
            case Failure( e : TenantNotFoundException ) => {
                e.getMessage should include( "id: non-existent-fake-tenant" )
                noException should be thrownBy( Await.result( index.tenant( "tenant-id-1" ), 5.second ) )
            }
            case Failure( e ) => fail( s"threw the wrong error: $e" )
            case Success( res ) => fail( s"failed to fail: $res" )
        }
    }

    it should "throw TenantNotFoundException when attempting to add documents to non-existing tenant" in {
        Try( Await.result( index.addDocumentToTenant( "some-doc-id", "non-existent-fake-tenant" ), 5.seconds ) ) match {
            case Failure( e : TenantNotFoundException ) => e.getMessage should include( "id: non-existent-fake-tenant" )
            case Failure( e ) => fail( s"threw the wrong error: $e" )
            case Success( res ) => fail( s"failed to fail: $res" )
        }
    }

    it should "throw TenantAlreadyExistsException when attempting to add a tenant that already exists" in {
        index.addTenant( "tenant-id-2" ).awaitWrite

        Try( Await.result( index.addTenant( "tenant-id-2" ), 5.seconds ) ) match {
            case Failure( e : TenantAlreadyExistsException ) => {
                e.getMessage should include( "id: tenant-id-2" )
            }
            case Failure( e ) => fail( s"threw the wrong error: $e" )
            case Success( res ) => fail( s"failed to fail: $res" )
        }

        Try( Await.result( index.addTenants( List( "tenant-id-3", "non-existent-fake-tenant-1", "non-existent-fake-tenant-2", "tenant-id-2" ) ).logged, 5.seconds ) ) match {
            case Failure( e : TenantAlreadyExistsException ) => {
                e.getMessage should include( "id: tenant-id-2" )
                a[ TenantNotFoundException ] should be thrownBy( Await.result( index.tenant( "tenant-id-3" ), 5.seconds ) )
            }
            case Failure( e ) => fail( s"threw the wrong error: $e" )
            case Success( res ) => fail( s"failed to fail: $res" )
        }
    }

    it should "throw InvalidTenantIdException when attempting to add tenant with invalid id" in {
        Try( Await.result( index.addTenant( "tenant:id~2" ), 5.seconds ) ) match {
            case Failure( e : InvalidTenantIdException ) => {
                e.getMessage should include( "id tenant:id~2" )
            }
            case Failure( e ) => fail( s"threw the wrong error: $e" )
            case Success( res ) => fail( s"failed to fail: $res" )
        }

        Try( Await.result( index.addTenants( List( "valid-tenant-1", "INVALID", "valid-tenant-2", "also###invalid" ) ), 5.seconds ) ) match {
            case Failure( e : InvalidTenantIdException ) => {
                e.getMessage should include( "id INVALID" )
                Thread.sleep( 1000 )
                a[ TenantNotFoundException ] should be thrownBy( Await.result( index.tenant( "valid-tenant-1" ), 5.seconds ) )
            }
            case Failure( e ) => fail( s"threw the wrong error: $e" )
            case Success( res ) => fail( s"failed to fail: $res" )
        }
    }

    it should "throw DocIdMissingFromTenantException when attempting to delete non-existant document from a tenant" in {
        index.addTenant( originalTenants.head ).awaitWrite
        index.addDocumentsToTenant( docIds.drop( 1 ), originalTenants.head.id ).awaitWrite

        Try( index.removeDocumentFromTenant( docIds.head, originalTenants.head.id ).awaitWrite ) match {
            case Success( _ ) => fail( "Failed to throw exception" )
            case Failure( e : DocIdMissingFromTenantException ) => e.getMessage should include( docIds.head )
            case Failure( e ) => fail( s"Threw the wrong exception! ${e.getMessage}" )
        }
    }

    it should "successfully clone a tenant" in {
        index.addTenant( originalTenants.head ).awaitWrite
        index.addDocumentsToTenant( docIds, originalTenants.head.id ).awaitWrite

        index.cloneTenant( originalTenants.head, "new-tenant" ).awaitWrite

        val newTenant : CorpusTenant = index.tenant( "new-tenant" ).await
        val indexDocs : Seq[ String ] = index.tenantDocuments( newTenant ).await

        indexDocs.toSet shouldBe docIds.toSet
    }

}

class CorpusTenantIndexDefaultCloneMethodTest extends AnyFlatSpecLike with Matchers with TestUtils {

    behavior of "CorpusTenantIndex.cloneTenant"

    it should "rollback cloned tenant when it fails to copy documents over" in {
        val index = new InMemoryCorpusTenantIndex()

        val testId = "test-tenant-id"
        val newId = "new-tenant-id"
        val docIds = Seq( "test-doc-id-1", "test-doc-id-2", "test-doc-id-3" )
        val testException = new NullPointerException( "test-exception" )

        val stubbedTenant = new CorpusTenantIndex {
            // Mocked method -- return a failure when you try to add it to newId
            override def addDocumentsToTenants( docIds : Iterable[DocId ], tenantIds : Iterable[TenantId ] ) : Future[ Unit ] = {
                if ( tenantIds.toSeq.contains( testId ) ) index.addDocumentsToTenants( docIds, tenantIds )
                else Future.failed( testException )
            }

            // Everything else should inherit from tested index
            override implicit val executionContext : ExecutionContext = index.executionContext
            override def allTenants : Future[Seq[CorpusTenant ] ] = index.allTenants
            override def tenant( tenantId : TenantId ) : Future[CorpusTenant ] = index.tenant( tenantId )
            override def tenantDocuments( tenantId : TenantId ) : Future[Seq[DocId ] ] = index.tenantDocuments( tenantId )
            override def documentTenants( docId : DocId ) : Future[Seq[CorpusTenant ] ] = index.documentTenants( docId )
            override def addTenants( tenantIds : Iterable[TenantId ] ) : Future[ Unit ] = index.addTenants( tenantIds )
            override def removeTenants( tenantIds : Iterable[TenantId ] ) : Future[ Unit ] = index.removeTenants( tenantIds )
            override def removeDocumentsFromTenants( docIds : Iterable[DocId ], tenantIds : Iterable[TenantId ] ) : Future[ Unit ] = ???
            override def removeDocumentsFromIndex( docIds : Iterable[DocId ] ) : Future[ Unit ] = ???
        }

        stubbedTenant.addTenants( Some( testId ) ).awaitWrite
        stubbedTenant.addDocumentsToTenants( docIds, Some( testId ) ).awaitWrite

        Try( stubbedTenant.cloneTenant( testId, newId ).await ) match {
            case Success( _ ) => fail( "Should have failed to add documents to tenants" )
            case Failure( e : NullPointerException ) =>
                e.getMessage shouldBe testException.getMessage
                a [ TenantNotFoundException ] should be thrownBy( stubbedTenant.tenant( newId ).await )
            case Failure( e ) => fail( s"Wrong exception! ${e.getMessage}" )
        }
    }

    it should "throw NonAtomicTenantIndexFailureException when it fails to roll back a cloned tenant after failing to copy documents over" in {
        val index = new InMemoryCorpusTenantIndex()

        val testId = "test-tenant-id"
        val newId = "new-tenant-id"
        val docIds = Seq( "test-doc-id-1", "test-doc-id-2", "test-doc-id-3" )
        val testException = new NullPointerException( "test-exception" )

        val stubbedTenant = new CorpusTenantIndex {
            // Mocked method -- return a failure when you try to add it to newId
            override def addDocumentsToTenants( docIds : Iterable[DocId ], tenantIds : Iterable[TenantId ] ) : Future[ Unit ] = {
                if ( tenantIds.toSeq.contains( testId ) ) index.addDocumentsToTenants( docIds, tenantIds )
                else Future.failed( testException )
            }

            // Everything else should inherit from tested index
            override implicit val executionContext : ExecutionContext = index.executionContext
            override def allTenants : Future[Seq[CorpusTenant ] ] = index.allTenants
            override def tenant( tenantId : TenantId ) : Future[CorpusTenant ] = index.tenant( tenantId )
            override def tenantDocuments( tenantId : TenantId ) : Future[Seq[DocId ] ] = index.tenantDocuments( tenantId )
            override def documentTenants( docId : DocId ) : Future[Seq[CorpusTenant ] ] = index.documentTenants( docId )
            override def addTenants( tenantIds : Iterable[TenantId ] ) : Future[ Unit ] = index.addTenants( tenantIds )
            override def removeTenants( tenantIds : Iterable[TenantId ] ) : Future[ Unit ] = index.removeTenants( tenantIds )
            override def removeDocumentsFromTenants( docIds : Iterable[DocId ], tenantIds : Iterable[TenantId ] ) : Future[ Unit ] = ???
            override def removeDocumentsFromIndex( docIds : Iterable[DocId ] ) : Future[ Unit ] = ???
        }

        stubbedTenant.addTenants( Some( testId ) ).awaitWrite
        stubbedTenant.addDocumentsToTenants( docIds, Some( testId ) ).awaitWrite

        Try( stubbedTenant.cloneTenant( testId, newId ).await ) match {
            case Success( _ ) => fail( "Should have failed to add documents to tenants" )
            case Failure( e : NullPointerException ) =>
                e.getMessage shouldBe testException.getMessage
                a [ TenantNotFoundException ] should be thrownBy( stubbedTenant.tenant( newId ).await )
            case Failure( e ) => fail( s"Wrong exception! ${e.getMessage}" )
        }
    }

}
