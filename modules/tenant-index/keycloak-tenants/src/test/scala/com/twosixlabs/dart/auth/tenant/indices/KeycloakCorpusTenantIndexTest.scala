package com.twosixlabs.dart.auth.tenant.indices

import annotations.IntegrationTest
import com.twosixlabs.dart.auth.keycloak.KeycloakAdminClient
import com.twosixlabs.dart.auth.tenant.CorpusTenant
import com.twosixlabs.dart.auth.tenant.CorpusTenantIndex.{InvalidTenantIdException, TenantAlreadyExistsException, TenantNotFoundException}
import org.scalatest.{BeforeAndAfterEach, Ignore}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@IntegrationTest
@Ignore
class KeycloakCorpusTenantIndexTest
  extends AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterEach {

    val writeTimeout : Duration = 1.second

    implicit class AwaitableFuture[ T ]( fut : Future[ T ] ) {
        def await( timeout : Duration ) : T = Await.result( fut, timeout )

        def await5 : T = await( 5.seconds )

        def awaitLong : T = await( 20.minutes )

        def awaitWrite : T = {
            val res = await5
            Thread.sleep( writeTimeout.toMillis )
            res
        }
    }

    val index : KeycloakCorpusTenantIndex = KeycloakCorpusTenantIndex(
        KeycloakAdminClient(
            "http",
            "localhost",
            8090,
            realm = "dart",
            adminRealm = "dart",
            adminClientId = "dart-admin",
            adminClientSecret = "a5dd2106-326b-4b2b-bf0a-c4afd5e86851",
        )
    )

    implicit val ec : ExecutionContext = index.executionContext

    override def beforeEach( ) : Unit = {
        index.allTenants.flatMap( tenants => Future.sequence( tenants.map( v => index.removeTenant( v ) ) ) ).awaitWrite
        super.beforeEach()
    }

    val testTenant1 = CorpusTenant( "test-tenant-1" )
    val testTenant2 = CorpusTenant( "test-tenant-2" )
    val testTenant3 = CorpusTenant( "test-tenant-3" )
    val invalidTenant = CorpusTenant( "test~tenant!4" )

    behavior of "KeycloakCorpusTenantIndex.addTenant"

    it should "add a tenant" in {
        index.addTenant( testTenant1 ).awaitWrite
        val tenants = index.allTenants.await5
        tenants.length shouldBe 1
        tenants.head shouldBe testTenant1
    }

    it should "add multiple tenants" in {
        index.addTenant( testTenant1, testTenant2, testTenant3 ).awaitWrite
        val tenants = index.allTenants.await5
        tenants.length shouldBe 3
        tenants.toSet shouldBe Set( testTenant3, testTenant2, testTenant1 )
    }

    it should "throw an InvalidTenantIdException if tenant id is invalid and not add tenant" in {
        Try( index.addTenant( invalidTenant ).awaitWrite ) match {
            case Success( _ ) => fail( "Did not throw any exception!" )
            case Failure( e : InvalidTenantIdException ) => e.getMessage should include( invalidTenant.id )
            case Failure( e ) => fail( s"threw the wrong exception!: ${e.getMessage}" )
        }
        val tenants = index.allTenants.await5
        tenants.isEmpty shouldBe true
    }

    it should "throw an InvalidTenantIdException and not add any tenants if only one tenant id of many is invalid " in {
        Try( index.addTenant( testTenant1, testTenant2, invalidTenant, testTenant3 ).awaitWrite ) match {
            case Success( _ ) => fail( "Did not throw any exception!" )
            case Failure( e : InvalidTenantIdException ) => e.getMessage should include( invalidTenant.id )
            case Failure( e ) => fail( s"threw the wrong exception!: ${e.getMessage}" )
        }
        val tenants = index.allTenants.await5
        tenants.isEmpty shouldBe true
    }

    it should "throw a TenantAlreadyExistsException if tenant is already in index" in {
        index.addTenant( testTenant1 ).awaitWrite
        index.allTenants.await5 shouldBe Seq( testTenant1 )
        Try( index.addTenant( testTenant1 ).awaitWrite ) match {
            case Success( _ ) => fail( "Did not throw any exception!" )
            case Failure( e : TenantAlreadyExistsException ) => e.getMessage should include( testTenant1.id )
            case Failure( e ) => fail( s"threw the wrong exception!: ${e.getMessage}" )
        }
    }

    it should "throw a TenantAlreadyExistsException and not add any tenants if one tenant of many is already in index" in {
        index.addTenant( testTenant1 ).awaitWrite
        index.allTenants.await5 shouldBe Seq( testTenant1 )
        Try( index.addTenant( testTenant2, testTenant3, testTenant1 ).awaitWrite ) match {
            case Success( _ ) => fail( "Did not throw any exception!" )
            case Failure( e : TenantAlreadyExistsException ) => e.getMessage should include( testTenant1.id )
            case Failure( e ) => fail( s"threw the wrong exception!: ${e.getMessage}" )
        }
        index.allTenants.await5 shouldBe Seq( testTenant1 )

    }

    behavior of "KeycloakCorpusTenantIndex.tenant"

    it should "retrieve a single tenant that exists in the index" in {
        index.addTenant( testTenant1 ).awaitWrite
        index.tenant( testTenant1.id ).await5 shouldBe testTenant1
    }

    it should "retrieve a single tenant when similar name tenant exists in the index" in {
        val testTenant = CorpusTenant( "tenant" )
        val testTenantSpecA = CorpusTenant( "a-tenant-spec" )
        index.addTenant(testTenant1, testTenant, testTenantSpecA).awaitWrite
        index.tenant( testTenant.id ).await5 shouldBe testTenant
    }

    it should "return TenantNotFoundException if tenant is not in the index" in {
        Try( index.tenant( testTenant1.id ).await5 ) match {
            case Success( _ ) => fail( "Did not throw any exception!" )
            case Failure( e : TenantNotFoundException ) => e.getMessage should include( testTenant1.id )
            case Failure( e ) => fail( s"threw the wrong exception!: ${e.getMessage}" )
        }
    }

    behavior of "KeycloakCorpusTenantIndex.removeTenant"

    it should "remove a tenant that exists in the index" in {
        index.addTenant( testTenant1 ).awaitWrite
        index.allTenants.await5 should contain( testTenant1 )
        index.removeTenant( testTenant1 ).awaitWrite
        index.allTenants.await5 should not contain( testTenant1 )
    }

    it should "remove multiple tenants that exist in the index" in {
        index.addTenant( testTenant1, testTenant2, testTenant3 ).awaitWrite
        Set( testTenant1, testTenant2, testTenant3 ).subsetOf( index.allTenants.await5.toSet ) shouldBe true
        index.removeTenant( testTenant1.id, testTenant2.id, testTenant3.id ).awaitWrite
        index.allTenants.await5.toSet.intersect( Set( testTenant1, testTenant2, testTenant3 ) ).isEmpty shouldBe true
    }

    it should "throw a TenantNotFoundException if tenant is not in the index" in {
        Try( index.removeTenant( testTenant1 ).awaitWrite ) match {
            case Success( _ ) => fail( "Did not throw any exception!" )
            case Failure( e : TenantNotFoundException ) => e.getMessage should include( testTenant1.id )
            case Failure( e ) => fail( s"threw the wrong exception!: ${e.getMessage}" )
        }
    }

    it should "throw a TenantNotFoundException and not remove any tenants if one tenant of many is not in the index" in {
        index.addTenant( testTenant1, testTenant2 ).awaitWrite
        index.allTenants.await5.length shouldBe 2
        Try( index.removeTenant( testTenant1.id, testTenant2.id, testTenant3.id ).await5 ) match {
            case Success( _ ) => fail( "Did not throw any exception!" )
            case Failure( e : TenantNotFoundException ) => e.getMessage should include( testTenant3.id )
            case Failure( e ) => fail( s"threw the wrong exception!: ${e.getMessage}" )
        }
        index.allTenants.await5.toSet shouldBe Set( testTenant1, testTenant2 )
    }

}
