package com.twosixlabs.dart.auth.tenant.indices

import com.twosixlabs.dart.auth.tenant.indices.ParallelCorpusTenantIndex.NonAtomicParallelIndexFailureException
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, CorpusTenantIndex, CorpusTenantIndexTest}
import org.scalamock.function.MockFunction1
import org.scalamock.scalatest.MockFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class ParallelCorpusTenantIndexTest
  extends CorpusTenantIndexTest(
      new ParallelCorpusTenantIndex(
          new InMemoryCorpusTenantIndex(),
          new InMemoryCorpusTenantIndex(),
          new InMemoryCorpusTenantIndex(),
      )
  ) with MockFactory {

    override def beforeEach( ) : Unit = {
        index.allIndices.foreach( ind => {
            ind.allTenants.flatMap( tenants => Future.sequence( tenants.map( v => ind.removeTenant( v ) ) ) ).awaitWrite
        } )
        super.beforeEach()
    }

    class StubbedTenantIndex extends CorpusTenantIndex {
        override implicit val executionContext : ExecutionContext = scala.concurrent.ExecutionContext.global

        val allTenantsMock = mockFunction[ Future[ Seq[ CorpusTenant ] ] ]
        override def allTenants : Future[ Seq[ CorpusTenant ] ] = allTenantsMock()

        val tenantMock = mockFunction[ TenantId, Future[ CorpusTenant ] ]
        override def tenant( tenantId : TenantId ) : Future[ CorpusTenant ] = tenantMock( tenantId )

        val tenantDocumentsMock = mockFunction[ TenantId, Future[ Seq[ DocId ] ] ]
        override def tenantDocuments( tenantId : TenantId ) : Future[ Seq[ DocId ] ] = tenantDocumentsMock( tenantId )

        val documentTenantsMock = mockFunction[ DocId, Future[ Seq[ CorpusTenant ] ] ]
        override def documentTenants( docId : DocId ) : Future[ Seq[ CorpusTenant ] ] = documentTenantsMock( docId )

        val addTenantsMock : MockFunction1[Iterable[TenantId ], Future[ Unit ] ] = mockFunction[ Iterable[ TenantId ], Future[ Unit ] ]
        override def addTenants( tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = addTenantsMock( tenantIds )

        val removeTenantsMock = mockFunction[ Iterable[ TenantId ], Future[ Unit ] ]
        override def removeTenants( tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = removeTenantsMock( tenantIds )

        val addDocumentsToTenantsMock = mockFunction[ Iterable[ DocId ], Iterable[ TenantId ], Future[ Unit ] ]
        override def addDocumentsToTenants( docIds : Iterable[ DocId ],
                                            tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = addDocumentsToTenantsMock( docIds, tenantIds )

        val removeDocumentsFromTenantsMock = mockFunction[ Iterable[ DocId ], Iterable[ TenantId ], Future[ Unit ] ]
        override def removeDocumentsFromTenants( docIds : Iterable[ DocId ],
                                                 tenantIds : Iterable[ TenantId ] ) : Future[ Unit ] = removeDocumentsFromTenantsMock( docIds, tenantIds )

        val removeDocumentsFromIndexMock = mockFunction[ Iterable[ DocId ], Future[ Unit ] ]
        override def removeDocumentsFromIndex( docIds : Iterable[ DocId ] ) : Future[ Unit ] = removeDocumentsFromIndexMock( docIds )
    }

    val index1 = new StubbedTenantIndex
    val index2 = new StubbedTenantIndex
    val index3 = new StubbedTenantIndex
    val index4 = new StubbedTenantIndex

    val parallelIndex = new ParallelCorpusTenantIndex(
        index1, index2, index3, index4,
     )

    behavior of "Parallel updates"

    it should "call all indices when adding tenants successfully" in {
        ( index1.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )
        ( index2.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )
        ( index3.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )
        ( index4.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )

        parallelIndex.addTenant( "test-tenant" ).await shouldBe ()
    }

    it should "return exception when all tenants fail to add tenants" in {
        ( index1.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.failed( new NullPointerException( "test-exception" ) ) )
        ( index2.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.failed( new NullPointerException( "test-exception" ) ) )
        ( index3.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.failed( new NullPointerException( "test-exception" ) ) )
        ( index4.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.failed( new NullPointerException( "test-exception" ) ) )

        Try( parallelIndex.addTenant( "test-tenant" ).await ) match {
            case Success( _ ) => fail( "Failed to throw exception!" )
            case Failure( e : NullPointerException ) => e.getMessage should include( "test-exception" )
            case Failure( e ) => fail( s"Threw the wrong exception!: ${e.toString}" )
        }
    }

    it should "call rollback functions on indices that succeed when one fails, and return that one's exception" in {
        ( index1.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )
        ( index2.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )
        ( index3.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.failed( new NullPointerException( "test-exception" ) ) )
        ( index4.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )

        // Rollbacks
        ( index1.removeTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )
        ( index2.removeTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )
        ( index3.removeTenantsMock ).expects( Seq( "test-tenant"  ) ).never()
        ( index4.removeTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )

        Try( parallelIndex.addTenant( "test-tenant" ).await ) match {
            case Success( _ ) => fail( "Failed to throw exception!" )
            case Failure( e : NullPointerException ) => e.getMessage should include( "test-exception" )
            case Failure( e ) => fail( s"Threw the wrong exception!: ${e.toString}" )
        }
    }

    it should "call rollback functions on indices that succeed when one fails, and return a NonAtomicTenantIndexFailureException when one of the rollbacks fails" in {
        ( index1.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )
        ( index2.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )
        ( index3.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.failed( new NullPointerException( "test-exception" ) ) )
        ( index4.addTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )

        // Rollbacks
        ( index1.removeTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )
        ( index2.removeTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.failed( new IllegalStateException( "test-exception" ) ) )
        ( index3.removeTenantsMock ).expects( Seq( "test-tenant"  ) ).never()
        ( index4.removeTenantsMock ).expects( Seq( "test-tenant"  ) ).once().returning( Future.successful() )

        Try( parallelIndex.addTenant( "test-tenant" ).await ) match {
            case Success( _ ) => fail( "Failed to throw exception!" )
            case Failure( e : NonAtomicParallelIndexFailureException ) =>
                e.originalException shouldBe a [NullPointerException]
                e.recoveryFailure shouldBe an [IllegalStateException]
            case Failure( e ) => fail( s"Threw the wrong exception!: ${e.toString}" )
        }
    }

    it should "call all indices when removing documents from index successfully" in {
        ( index1.documentTenantsMock ).expects( "test-doc-1" ).once().returning( Future.successful( Seq( CorpusTenant( "test-tenant-1" ) ) ) )
        ( index1.documentTenantsMock ).expects( "test-doc-2" ).once().returning( Future.successful( Seq( CorpusTenant( "test-tenant-1" ), CorpusTenant( "test-tenant-2" ) ) ) )

        ( index1.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.successful() )
        ( index2.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.successful() )
        ( index3.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.successful() )
        ( index4.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.successful() )

        parallelIndex.removeDocumentFromIndex( "test-doc-1", "test-doc-2" ).await shouldBe ()
    }

    it should "return exception when all tenants fail to remove docs from index" in {
        ( index1.documentTenantsMock ).expects( "test-doc-1" ).once().returning( Future.successful( Seq( CorpusTenant( "test-tenant-1" ) ) ) )
        ( index1.documentTenantsMock ).expects( "test-doc-2" ).once().returning( Future.successful( Seq( CorpusTenant( "test-tenant-1" ), CorpusTenant( "test-tenant-2" ) ) ) )

        ( index1.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.failed( new NullPointerException( "test-exception" ) ) )
        ( index2.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.failed( new NullPointerException( "test-exception" ) ) )
        ( index3.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.failed( new NullPointerException( "test-exception" ) ) )
        ( index4.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.failed( new NullPointerException( "test-exception" ) ) )

        Try( parallelIndex.removeDocumentFromIndex( "test-doc-1", "test-doc-2" ).await ) match {
            case Success( _ ) => fail( "Failed to throw exception!" )
            case Failure( e : NullPointerException ) => e.getMessage should include( "test-exception" )
            case Failure( e ) => fail( s"Threw the wrong exception!: ${e.toString}" )
        }
    }

    it should "call rollback functions on indices that succeed when one fails to remove docs from index, and return that one's exception" in {
        ( index1.documentTenantsMock ).expects( "test-doc-1" ).once().returning( Future.successful( Seq( CorpusTenant( "test-tenant-1" ) ) ) )
        ( index1.documentTenantsMock ).expects( "test-doc-2" ).once().returning( Future.successful( Seq( CorpusTenant( "test-tenant-1" ), CorpusTenant( "test-tenant-2" ) ) ) )

        ( index1.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.successful() )
        ( index2.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.successful() )
        ( index3.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.failed( new NullPointerException( "test-exception" ) ) )
        ( index4.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.successful() )

        // Rollbacks
        ( index1.addDocumentsToTenantsMock ).expects( Seq( "test-doc-1" ), Seq( "test-tenant-1" ) ).once().returning( Future.successful() )
        ( index1.addDocumentsToTenantsMock ).expects( Seq( "test-doc-2" ), Seq( "test-tenant-1", "test-tenant-2" ) ).once().returning( Future.successful() )
        ( index2.addDocumentsToTenantsMock ).expects( Seq( "test-doc-1" ), Seq( "test-tenant-1" ) ).once().returning( Future.successful() )
        ( index2.addDocumentsToTenantsMock ).expects( Seq( "test-doc-2" ), Seq( "test-tenant-1", "test-tenant-2" ) ).once().returning( Future.successful() )
        ( index3.addDocumentsToTenantsMock ).expects( *, * ).never()
        ( index4.addDocumentsToTenantsMock ).expects( Seq( "test-doc-1" ), Seq( "test-tenant-1" ) ).once().returning( Future.successful() )
        ( index4.addDocumentsToTenantsMock ).expects( Seq( "test-doc-2" ), Seq( "test-tenant-1", "test-tenant-2" ) ).once().returning( Future.successful() )

        Try( parallelIndex.removeDocumentFromIndex( "test-doc-1", "test-doc-2" ).await ) match {
            case Success( _ ) => fail( "Failed to throw exception!" )
            case Failure( e : NullPointerException ) => e.getMessage should include( "test-exception" )
            case Failure( e ) => fail( s"Threw the wrong exception!: ${e.toString}" )
        }
    }

    it should "call rollback functions on indices that succeed when one fails to remove docs from index, and return a NonAtomicTenantIndexFailureException when one of the rollbacks fails" in {
        ( index1.documentTenantsMock ).expects( "test-doc-1" ).once().returning( Future.successful( Seq( CorpusTenant( "test-tenant-1" ) ) ) )
        ( index1.documentTenantsMock ).expects( "test-doc-2" ).once().returning( Future.successful( Seq( CorpusTenant( "test-tenant-1" ), CorpusTenant( "test-tenant-2" ) ) ) )

        ( index1.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.successful() )
        ( index2.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.successful() )
        ( index3.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.failed( new NullPointerException( "test-exception" ) ) )
        ( index4.removeDocumentsFromIndexMock ).expects( Seq( "test-doc-1", "test-doc-2" ) ).once().returning( Future.successful() )

        // Rollbacks
        ( index1.addDocumentsToTenantsMock ).expects( Seq( "test-doc-1" ), Seq( "test-tenant-1" ) ).once().returning( Future.successful() )
        ( index1.addDocumentsToTenantsMock ).expects( Seq( "test-doc-2" ), Seq( "test-tenant-1", "test-tenant-2" ) ).once().returning( Future.successful() )
        ( index2.addDocumentsToTenantsMock ).expects( Seq( "test-doc-1" ), Seq( "test-tenant-1" ) ).once().returning( Future.failed( new IllegalStateException( "test-exception" ) ) )
        ( index2.addDocumentsToTenantsMock ).expects( Seq( "test-doc-2" ), Seq( "test-tenant-1", "test-tenant-2" ) ).once().returning( Future.successful() )
        ( index3.addDocumentsToTenantsMock ).expects( *, * ).never()
        ( index4.addDocumentsToTenantsMock ).expects( Seq( "test-doc-1" ), Seq( "test-tenant-1" ) ).once().returning( Future.successful() )
        ( index4.addDocumentsToTenantsMock ).expects( Seq( "test-doc-2" ), Seq( "test-tenant-1", "test-tenant-2" ) ).once().returning( Future.successful() )

        Try( parallelIndex.removeDocumentFromIndex( "test-doc-1", "test-doc-2" ).await ) match {
            case Success( _ ) => fail( "Failed to throw exception!" )
            case Failure( e : NonAtomicParallelIndexFailureException ) =>
                e.originalException shouldBe a [NullPointerException]
                e.recoveryFailure shouldBe an [IllegalStateException]
            case Failure( e ) => fail( s"Threw the wrong exception!: ${e.toString}" )
        }
    }
}
