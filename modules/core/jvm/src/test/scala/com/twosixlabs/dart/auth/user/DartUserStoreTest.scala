package com.twosixlabs.dart.auth.user

import com.twosixlabs.dart.auth.groups.{DartGroup, TenantGroup}
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, CorpusTenantIndex, GlobalCorpus, Leader, Member, ReadOnly}
import com.twosixlabs.dart.auth.user.DartUserStore.{InvalidUserNameException, UserAlreadyExistsException, UserNotFoundException}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.{Duration, DurationLong}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class DartUserStoreTest( userStore : DartUserStore,
                         tenantIndex : CorpusTenantIndex,
                         val timeOutMs : Long = 5000,
                         val writeDelayMs : Long = 0 ) extends AnyFlatSpecLike with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

    val className : String = userStore.getClass.getSimpleName
    val LOG : Logger = LoggerFactory.getLogger( getClass )

    implicit val ec : ExecutionContext = userStore.ec

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

    val tenant1 : CorpusTenant = CorpusTenant( "tenant-one" )
    val tenant2 : CorpusTenant = CorpusTenant( "tenant-two" )
    val tenant3 : CorpusTenant = CorpusTenant( "tenant-three" )

    val dartGroup1 : DartGroup = TenantGroup( GlobalCorpus, Leader )
    val dartGroup2 : DartGroup = TenantGroup( tenant1, Member )
    val dartGroup3 : DartGroup = TenantGroup( tenant1, ReadOnly )
    val dartGroup4 : DartGroup = TenantGroup( tenant2, Leader )
    val dartGroup5 : DartGroup = TenantGroup( tenant2, ReadOnly )

    val dartUser1 : DartUser = DartUser( "dart-user-1", Set( dartGroup1, dartGroup2 ), DartUserInfo( Some( "FirstName" ), Some( "LastName" ), Some( "email@address.com" ) ) )
    val dartUser2 : DartUser = DartUser( "dart-user-2", Set( dartGroup2, dartGroup3 ) )
    val dartUser3 : DartUser = DartUser( "dart-user-3", Set( dartGroup3, dartGroup4, dartGroup5 ) )
    val invalidUserNameUser : DartUser = DartUser( "dart~user!4", Set() )

    override def beforeAll( ) : Unit = {
        super.beforeAll()
        tenantIndex.addTenant(tenant3, tenant1, tenant2)
    }

    override def afterAll( ) : Unit = {
        super.afterAll()
        tenantIndex.removeTenant(tenant3.id, tenant1.id, tenant2.id)
    }

    override def beforeEach( ) : Unit = {
        userStore.allUsers.flatMap( users => userStore.removeUsers( users.map( _.userName ).toSeq ) ).awaitWrite
        require( userStore.allUsers.await.isEmpty )
        super.beforeEach()
    }

    override def afterEach( ) : Unit = {
        super.afterEach()
        userStore.allUsers.flatMap( users => userStore.removeUsers( users.map( _.userName ).toSeq ) ).awaitWrite
        require( userStore.allUsers.await.isEmpty )
    }

    s"$className.allUsers" should "return an empty seq if there are no users" in {
        userStore.allUsers.await.isEmpty shouldBe true
    }

    behavior of s"$className.addUser"

    it should "add a user" in {
        userStore.addUser( dartUser1 ).awaitWrite
        userStore.allUsers.await shouldBe Set( dartUser1 )
        userStore.user( dartUser1.userName ).await shouldBe dartUser1
    }

        it should "throw a UserAlreadyExistsException if user already exists and not add user" in {
            userStore.addUser( dartUser1 ).awaitWrite
            Try( userStore.addUser( dartUser1 ).awaitWrite ) match {
                case Success( _ ) => fail( s"User store added an already existing user!" )
                case Failure( e : UserAlreadyExistsException ) => e.getMessage should include( dartUser1.userName )
                case Failure( e ) => fail( s"Threw the wrong exception!: ${e.getMessage}" )
            }
            userStore.allUsers.await should have size( 1 )
        }

        it should "throw a InvalidUserNameException if user has invalid name" in {
            Try( userStore.addUser( invalidUserNameUser ).awaitWrite ) match {
                case Success( _ ) => fail( s"User store added a user with an invalid name!" )
                case Failure( e : InvalidUserNameException ) => e.getMessage should include( invalidUserNameUser.userName )
                case Failure( e ) => fail( s"Threw the wrong exception!: ${e.getMessage}" )
            }
        }

        it should "add multiple users" in {
            userStore.addUser( dartUser1, dartUser2, dartUser3 ).awaitWrite
            userStore.allUsers.await shouldBe Set( dartUser1, dartUser2, dartUser3 )
        }

        it should "throw a UserAlreadyExistsException if one of the users already exists and not add any users" in {
            userStore.addUser( dartUser3 ).awaitWrite
            userStore.allUsers.await shouldBe Set( dartUser3 )
            Try( userStore.addUser( dartUser1, dartUser2, dartUser3 ).awaitWrite ) match {
                case Success( _ ) => fail( s"User store added an already existing user!" )
                case Failure( e : UserAlreadyExistsException ) => e.getMessage should include( dartUser3.userName )
                case Failure( e ) => fail( s"Threw the wrong exception!: ${e.getMessage}" )
            }
            userStore.allUsers.await shouldBe Set( dartUser3 )
        }

        it should "throw an InvalidUserNameException if one of the user names is invalid and not add any users" in {
            Try( userStore.addUser( dartUser1, dartUser2, invalidUserNameUser, dartUser3 ).awaitWrite ) match {
                case Success( _ ) => fail( s"User store added a user with an invalid name!" )
                case Failure( e : InvalidUserNameException ) => e.getMessage should include( invalidUserNameUser.userName )
                case Failure( e ) => fail( s"Threw the wrong exception!: ${e.getMessage}" )
            }
            userStore.allUsers.await should have size( 0 )
        }

        behavior of s"$className.removeUser"

        it should "remove a user that exists in user store" in {
            userStore.addUser( dartUser1 ).awaitWrite
            userStore.allUsers.await shouldBe Set( dartUser1 )
            userStore.removeUser( dartUser1 ).awaitWrite
            userStore.allUsers.await.isEmpty shouldBe true
        }

        it should "remove multiple users that exist in user store" in {
            userStore.addUser( dartUser1, dartUser2, dartUser3 ).awaitWrite
            userStore.allUsers.await shouldBe Set( dartUser1, dartUser2, dartUser3 )
            userStore.removeUser( dartUser1, dartUser2, dartUser3 ).awaitWrite
            userStore.allUsers.await.isEmpty shouldBe true
        }

        it should "throw a UserNotFoundException when single user is not in user store" in {
            Try( userStore.removeUser( dartUser1 ).awaitWrite ) match {
                case Success( _ ) => fail( s"User store removed non-existent user!" )
                case Failure( e : UserNotFoundException ) => e.getMessage should include( dartUser1.userName )
                case Failure( e ) => fail( s"Threw the wrong exception!: ${e.getMessage}" )
            }
            userStore.allUsers.await.size shouldBe 0
        }

        it should "throw a UserNotFoundException when multiple users are removed and one is not in user store and not remove any of them" in {
            userStore.addUser( dartUser1, dartUser2, dartUser3 ).awaitWrite
            userStore.allUsers.await shouldBe Set( dartUser1, dartUser2, dartUser3 )
            Try( userStore.removeUser( dartUser1, dartUser2, dartUser3, DartUser( "dart-user-4", Set() ) ).awaitWrite ) match {
                case Success( _ ) => fail( s"User store removed non-existent user!" )
                case Failure( e : UserNotFoundException ) => e.getMessage should include( "dart-user-4" )
                case Failure( e ) => fail( s"Threw the wrong exception!: ${e.getMessage}" )
            }
            userStore.allUsers.await shouldBe Set( dartUser1, dartUser2, dartUser3 )
        }

        behavior of s"$className.updateUser"

        it should "update a user" in {
            userStore.addUser( dartUser1 ).awaitWrite
            userStore.allUsers.await shouldBe Set( dartUser1 )
            userStore.updateUser( dartUser1.copy( groups = Set(), userInfo = dartUser1.userInfo.copy( firstName = Some( "NewFirstName" ), lastName = Some( "NewLastName" ) ) ) ).awaitWrite
            val users = userStore.allUsers.await
            users should have size( 1 )
            users.head.userInfo.firstName shouldBe Some( "NewFirstName" )
            users.head.userInfo.lastName shouldBe Some( "NewLastName" )
            users.head.userInfo.email shouldBe dartUser1.userInfo.email
            users.head.groups should have size( 0 )
        }

        it should "update multiple users" in {
            userStore.addUser( dartUser1, dartUser2 ).awaitWrite
            userStore.allUsers.await shouldBe Set( dartUser1, dartUser2 )
            userStore.updateUser( dartUser1.copy( groups = Set(), userInfo = dartUser1.userInfo.copy( firstName = Some( "NewFirstName" ), lastName = Some( "NewLastName" ) ) ),
                                  dartUser2.copy( userInfo = dartUser2.userInfo.copy( email = Some( "address@email.com" ) ) ) ).awaitWrite
            val users = userStore.allUsers.await
            users should have size( 2 )
            users should not be( Set( dartUser1, dartUser2 ) )
            val u1 = userStore.user( dartUser1.userName ).await
            val u2 = userStore.user( dartUser2.userName ).await
            u1.userInfo.firstName shouldBe Some( "NewFirstName" )
            u1.userInfo.lastName shouldBe Some( "NewLastName" )
            u1.userInfo.email shouldBe dartUser1.userInfo.email
            u1.groups should have size( 0 )
            u2.userInfo.firstName shouldBe None
            u2.userInfo.lastName shouldBe None
            u2.userInfo.email shouldBe Some( "address@email.com" )
        }

        it should "throw a UserNotFoundException if the user is not in the user store and not add user" in {
            Try( userStore.updateUser( dartUser1.copy( groups = Set(), userInfo = dartUser1.userInfo.copy( firstName = Some( "NewFirstName" ), lastName = Some( "NewLastName" ) ) ) ).awaitWrite ) match {
                case Success( _ ) => fail( s"User store removed non-existent user!" )
                case Failure( e : UserNotFoundException ) => e.getMessage should include( dartUser1.userName )
                case Failure( e ) => fail( s"Threw the wrong exception!: ${e.getMessage}" )
            }
            userStore.allUsers.await should have size( 0 )
        }

        it should "throw a UserNotFoundException and not update any users if even one user is not in user store" in {
            userStore.addUser( dartUser1, dartUser2 ).awaitWrite
            Try( userStore.updateUser( dartUser1.copy( groups = Set(), userInfo = dartUser1.userInfo.copy( firstName = Some( "NewFirstName" ), lastName = Some( "NewLastName" ) ) ),
                                       dartUser2.copy( groups = Set(), userInfo = DartUserInfo( firstName = Some( "TestName" ) ) ),
                                       dartUser3.copy( groups = dartUser1.groups, userInfo = dartUser1.userInfo ) ).awaitWrite ) match {
                case Success( _ ) => fail( s"User store removed non-existent user!" )
                case Failure( e : UserNotFoundException ) => e.getMessage should include( dartUser3.userName )
                case Failure( e ) => fail( s"Threw the wrong exception!: ${e.getMessage}" )
            }
            userStore.allUsers.await should have size( 2 )
            userStore.user( dartUser1.userName ).await shouldBe dartUser1
            userStore.user( dartUser2.userName ).await shouldBe dartUser2
        }

        behavior of s"$className.user"

        it should "get a user if user is in repo" in {
            userStore.addUser( dartUser1 ).awaitWrite
            userStore.user( dartUser1.userName ).await shouldBe dartUser1
        }

        it should "throw a UserNotFoundException if user is not in repo" in {
            Try( userStore.user( dartUser1.userName ).await ) match {
                case Success( usr ) => fail( s"User store returned a user when no user was added!: ${usr}" )
                case Failure( e : UserNotFoundException ) => e.getMessage should include( dartUser1.userName )
                case Failure( e ) => fail( s"Threw the wrong exception!: ${e.getMessage}" )
            }
        }


}
