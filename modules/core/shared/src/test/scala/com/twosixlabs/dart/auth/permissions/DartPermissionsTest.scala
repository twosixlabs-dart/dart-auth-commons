package com.twosixlabs.dart.auth.permissions

import com.twosixlabs.dart.auth.groups.{ProgramManager, TenantGroup}
import com.twosixlabs.dart.auth.permissions.DartOperations._
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, GlobalCorpus, Leader, Member, ReadOnly}
import com.twosixlabs.dart.auth.user.DartUser
import org.hungerford.rbac.Permission
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class DartPermissionsTest extends AnyFlatSpecLike with Matchers {

    val testTenant : CorpusTenant = CorpusTenant( "test" )

    behavior of "DartPermissions: TenantGroup with TenantRole of Leader"

    val leaderGroup : TenantGroup = TenantGroup( testTenant, Leader )
    val leaderGroupPermissions : Permission = DartPermissions.getPermissionsFromGroup( leaderGroup )

    val memberGroup : TenantGroup = TenantGroup( testTenant, Member )
    val memberGroupPermissions : Permission = DartPermissions.getPermissionsFromGroup( memberGroup )

    val readOnlyGroup : TenantGroup = TenantGroup( testTenant, ReadOnly )
    val readOnlyGroupPermissions : Permission = DartPermissions.getPermissionsFromGroup( readOnlyGroup )

    it should "be able to perform any OperationOnTenent other than write type TenantManagementOperation on the test tenant" in {
        leaderGroupPermissions.permits( TenantOperation( testTenant, AddDocument ) ) shouldBe true
        leaderGroupPermissions.permits( TenantOperation( testTenant, RemoveDocument ) ) shouldBe true
        leaderGroupPermissions.permits( TenantOperation( testTenant, RetrieveDocument ) ) shouldBe true
        leaderGroupPermissions.permits( TenantOperation( testTenant, UpdateDocument ) ) shouldBe true
        leaderGroupPermissions.permits( TenantOperation( testTenant, SearchCorpus ) ) shouldBe true
        leaderGroupPermissions.permits( TenantOperation( testTenant, RetrieveTenant ) ) shouldBe true
    }

    it should "not be able to perform any write type TenantManagementOperation on the test tenant" in {
        leaderGroupPermissions.permits( TenantOperation( testTenant, CreateTenant ) ) shouldBe false
        leaderGroupPermissions.permits( TenantOperation( testTenant, DeleteTenant ) ) shouldBe false
    }

    it should "not be able to perform any OperationOnTenant on the global corpus" in {
        leaderGroupPermissions.permits( TenantOperation( GlobalCorpus, AddDocument ) ) shouldBe false
        leaderGroupPermissions.permits( TenantOperation( GlobalCorpus, RemoveDocument ) ) shouldBe false
        leaderGroupPermissions.permits( TenantOperation( GlobalCorpus, RetrieveDocument ) ) shouldBe false
        leaderGroupPermissions.permits( TenantOperation( GlobalCorpus, UpdateDocument ) ) shouldBe false
        leaderGroupPermissions.permits( TenantOperation( GlobalCorpus, SearchCorpus ) ) shouldBe false
        leaderGroupPermissions.permits( TenantOperation( GlobalCorpus, CreateTenant ) ) shouldBe false
        leaderGroupPermissions.permits( TenantOperation( GlobalCorpus, DeleteTenant ) ) shouldBe false
        leaderGroupPermissions.permits( TenantOperation( GlobalCorpus, RetrieveTenant ) ) shouldBe false
    }

    it should "be able to perform any UserOperation with its own group" in {
        leaderGroupPermissions.permits( UserOperation( Set( leaderGroup ), RetrieveUser ) ) shouldBe true
        leaderGroupPermissions.permits( UserOperation( Set( leaderGroup ), AddUser ) ) shouldBe true
        leaderGroupPermissions.permits( UserOperation( Set( leaderGroup ), DeleteUser ) ) shouldBe true
        leaderGroupPermissions.permits( UserOperation( Set( leaderGroup ), UpdateUserRole ) ) shouldBe true
        leaderGroupPermissions.permits( UserOperation( Set( leaderGroup ), UpdateUserInfo ) ) shouldBe true
    }

    it should "be able to perform any UserOperation with a Member in its own tenant" in {
        leaderGroupPermissions.permits( UserOperation( Set( memberGroup ), RetrieveUser ) ) shouldBe true
        leaderGroupPermissions.permits( UserOperation( Set( memberGroup ), AddUser ) ) shouldBe true
        leaderGroupPermissions.permits( UserOperation( Set( memberGroup ), DeleteUser ) ) shouldBe true
        leaderGroupPermissions.permits( UserOperation( Set( memberGroup ), UpdateUserRole ) ) shouldBe true
        leaderGroupPermissions.permits( UserOperation( Set( memberGroup ), UpdateUserInfo ) ) shouldBe true
    }

    it should "not be able to perform any UserOperation with a ProgramManageer" in {
        leaderGroupPermissions.permits( UserOperation( Set( ProgramManager ), RetrieveUser ) ) shouldBe false
        leaderGroupPermissions.permits( UserOperation( Set( ProgramManager ), AddUser ) ) shouldBe false
        leaderGroupPermissions.permits( UserOperation( Set( ProgramManager ), DeleteUser ) ) shouldBe false
        leaderGroupPermissions.permits( UserOperation( Set( ProgramManager ), UpdateUserRole ) ) shouldBe false
        leaderGroupPermissions.permits( UserOperation( Set( ProgramManager ), UpdateUserInfo ) ) shouldBe false
    }

    it should "not be able to perform any UserOperation with a Leader in the global corpus" in {
        leaderGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), RetrieveUser ) ) shouldBe false
        leaderGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), AddUser ) ) shouldBe false
        leaderGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), DeleteUser ) ) shouldBe false
        leaderGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), UpdateUserRole ) ) shouldBe false
        leaderGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), UpdateUserInfo ) ) shouldBe false
    }

    it should "not be able to perform any UserOperation with a Member in the global corpus" in {
        leaderGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), RetrieveUser ) ) shouldBe false
        leaderGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), AddUser ) ) shouldBe false
        leaderGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), DeleteUser ) ) shouldBe false
        leaderGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), UpdateUserRole ) ) shouldBe false
        leaderGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), UpdateUserInfo ) ) shouldBe false
    }

    behavior of "DartPermissions: TenantGroup with TenantRole of Member"

    it should "be able to perform any OperationOnTenent other than write type TenantManagementOperation on the test tenant" in {
        memberGroupPermissions.permits( TenantOperation( testTenant, AddDocument ) ) shouldBe true
        memberGroupPermissions.permits( TenantOperation( testTenant, RemoveDocument ) ) shouldBe true
        memberGroupPermissions.permits( TenantOperation( testTenant, RetrieveDocument ) ) shouldBe true
        memberGroupPermissions.permits( TenantOperation( testTenant, UpdateDocument ) ) shouldBe true
        memberGroupPermissions.permits( TenantOperation( testTenant, SearchCorpus ) ) shouldBe true
        memberGroupPermissions.permits( TenantOperation( testTenant, RetrieveTenant ) ) shouldBe true
    }

    it should "not be able to perform any write type TenantManagementOperation on the test tenant" in {
        memberGroupPermissions.permits( TenantOperation( testTenant, CreateTenant ) ) shouldBe false
        memberGroupPermissions.permits( TenantOperation( testTenant, DeleteTenant ) ) shouldBe false
    }

    it should "not be able to perform any OperationOnTenant on the global corpus" in {
        memberGroupPermissions.permits( TenantOperation( GlobalCorpus, AddDocument ) ) shouldBe false
        memberGroupPermissions.permits( TenantOperation( GlobalCorpus, RemoveDocument ) ) shouldBe false
        memberGroupPermissions.permits( TenantOperation( GlobalCorpus, RetrieveDocument ) ) shouldBe false
        memberGroupPermissions.permits( TenantOperation( GlobalCorpus, UpdateDocument ) ) shouldBe false
        memberGroupPermissions.permits( TenantOperation( GlobalCorpus, SearchCorpus ) ) shouldBe false
        memberGroupPermissions.permits( TenantOperation( GlobalCorpus, CreateTenant ) ) shouldBe false
        memberGroupPermissions.permits( TenantOperation( GlobalCorpus, DeleteTenant ) ) shouldBe false
        memberGroupPermissions.permits( TenantOperation( GlobalCorpus, RetrieveTenant ) ) shouldBe false
    }

    it should "not be able to perform any UserOperation other than retrieve user on its own group" in {
        memberGroupPermissions.permits( UserOperation( Set( memberGroup ), RetrieveUser ) ) shouldBe true
        memberGroupPermissions.permits( UserOperation( Set( memberGroup ), AddUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( memberGroup ), DeleteUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( memberGroup ), UpdateUserRole ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( memberGroup ), UpdateUserInfo ) ) shouldBe false
    }

    it should "not be able to perform any UserOperation with a Leader in its own tenant" in {
        memberGroupPermissions.permits( UserOperation( Set( leaderGroup ), RetrieveUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( leaderGroup ), AddUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( leaderGroup ), DeleteUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( leaderGroup ), UpdateUserRole ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( leaderGroup ), UpdateUserInfo ) ) shouldBe false
    }

    it should "not be able to perform any UserOperation with a ProgramManageer" in {
        memberGroupPermissions.permits( UserOperation( Set( ProgramManager ), RetrieveUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( ProgramManager ), AddUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( ProgramManager ), DeleteUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( ProgramManager ), UpdateUserRole ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( ProgramManager ), UpdateUserInfo ) ) shouldBe false
    }

    it should "not be able to perform any UserOperation with a Leader in the global corpus" in {
        memberGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), RetrieveUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), AddUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), DeleteUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), UpdateUserRole ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), UpdateUserInfo ) ) shouldBe false
    }

    it should "not be able to perform any UserOperation with a Member in the global corpus" in {
        memberGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), RetrieveUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), AddUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), DeleteUser ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), UpdateUserRole ) ) shouldBe false
        memberGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), UpdateUserInfo ) ) shouldBe false
    }

    behavior of "DartPermissions: TenantGroup with TenantRole of ReadOnly"

    it should "be able to perform SearchCorpus, RetrieveDocument, and RetrieveTenant on the test tenant" in {
        readOnlyGroupPermissions.permits( TenantOperation( testTenant, RetrieveDocument ) ) shouldBe true
        readOnlyGroupPermissions.permits( TenantOperation( testTenant, SearchCorpus ) ) shouldBe true
        readOnlyGroupPermissions.permits( TenantOperation( testTenant, RetrieveTenant ) ) shouldBe true
    }

    it should "not be able to perform any OperationOnTenant operations besides SearchCorpus, RetrieveDocument, and RetrieveTenant on the test tenant" in {
        readOnlyGroupPermissions.permits( TenantOperation( testTenant, AddDocument ) ) shouldBe false
        readOnlyGroupPermissions.permits( TenantOperation( testTenant, RemoveDocument ) ) shouldBe false
        readOnlyGroupPermissions.permits( TenantOperation( testTenant, UpdateDocument ) ) shouldBe false
        readOnlyGroupPermissions.permits( TenantOperation( testTenant, CreateTenant ) ) shouldBe false
        readOnlyGroupPermissions.permits( TenantOperation( testTenant, DeleteTenant ) ) shouldBe false
    }

    it should "not be able to perform any OperationOnTenant on the global corpus" in {
        readOnlyGroupPermissions.permits( TenantOperation( GlobalCorpus, AddDocument ) ) shouldBe false
        readOnlyGroupPermissions.permits( TenantOperation( GlobalCorpus, RemoveDocument ) ) shouldBe false
        readOnlyGroupPermissions.permits( TenantOperation( GlobalCorpus, RetrieveDocument ) ) shouldBe false
        readOnlyGroupPermissions.permits( TenantOperation( GlobalCorpus, UpdateDocument ) ) shouldBe false
        readOnlyGroupPermissions.permits( TenantOperation( GlobalCorpus, SearchCorpus ) ) shouldBe false
        readOnlyGroupPermissions.permits( TenantOperation( GlobalCorpus, CreateTenant ) ) shouldBe false
        readOnlyGroupPermissions.permits( TenantOperation( GlobalCorpus, DeleteTenant ) ) shouldBe false
        readOnlyGroupPermissions.permits( TenantOperation( GlobalCorpus, RetrieveTenant ) ) shouldBe false
    }

    it should "not be able to perform any UserOperation except RetrieveUser with a ReadOnly in its own tenant" in {
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( testTenant, ReadOnly ) ), RetrieveUser ) ) shouldBe true
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( testTenant, ReadOnly ) ), AddUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( testTenant, ReadOnly ) ), DeleteUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( testTenant, ReadOnly ) ), UpdateUserRole ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( testTenant, ReadOnly ) ), UpdateUserInfo ) ) shouldBe false
    }

    it should "not be able to perform any UserOperation with a Member in its own tenant" in {
        readOnlyGroupPermissions.permits( UserOperation( Set( memberGroup ), RetrieveUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( memberGroup ), AddUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( memberGroup ), DeleteUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( memberGroup ), UpdateUserRole ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( memberGroup ), UpdateUserInfo ) ) shouldBe false
    }

    it should "not be able to perform any UserOperation with a Leader in its own tenant" in {
        readOnlyGroupPermissions.permits( UserOperation( Set( leaderGroup ), RetrieveUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( leaderGroup ), AddUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( leaderGroup ), DeleteUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( leaderGroup ), UpdateUserRole ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( leaderGroup ), UpdateUserInfo ) ) shouldBe false
    }

    it should "not be able to perform any UserOperation with a ProgramManageer" in {
        readOnlyGroupPermissions.permits( UserOperation( Set( ProgramManager ), RetrieveUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( ProgramManager ), AddUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( ProgramManager ), DeleteUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( ProgramManager ), UpdateUserRole ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( ProgramManager ), UpdateUserInfo ) ) shouldBe false
    }

    it should "not be able to perform any UserOperation with a Leader in the global corpus" in {
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), RetrieveUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), AddUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), DeleteUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), UpdateUserRole ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Leader ) ), UpdateUserInfo ) ) shouldBe false
    }

    it should "not be able to perform any UserOperation with a Member in the global corpus" in {
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), RetrieveUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), AddUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), DeleteUser ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), UpdateUserRole ) ) shouldBe false
        readOnlyGroupPermissions.permits( UserOperation( Set( TenantGroup( GlobalCorpus, Member ) ), UpdateUserInfo ) ) shouldBe false
    }

    "[DART-967]" should "allow a user with multiple groups to retrieve user with multiple groups if for all of the second user's groups there is exists a higher group for the first"  in {
        val user1 = DartUser( "test-user", Set( memberGroup, TenantGroup( GlobalCorpus, ReadOnly ) ) )

        user1.can( RetrieveUser.atLevel( user1.groups ) ) shouldBe true
    }
}
