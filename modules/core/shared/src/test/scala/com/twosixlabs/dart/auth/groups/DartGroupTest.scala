package com.twosixlabs.dart.auth.groups

import com.twosixlabs.dart.auth.tenant.{CorpusTenant, GlobalCorpus, Leader, Member, ReadOnly}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Success, Try}

class DartGroupTest extends AnyFlatSpecLike with Matchers {

    behavior of "DartGroup.fromString"

    it should "throw an IllegalArgumentException when an unknown format" in {
        val badName = "asfasdfa"
        Try( DartGroup.fromString( badName ) ) match {
            case Success( v ) => fail( s"returned $v instead of throwing exception" )
            case Failure( e : IllegalArgumentException ) => e.getMessage should include( badName )
            case Failure( e ) => fail( s"threw wrong exception: ${e.getMessage}" )
        }
    }

    it should """convert "program-manager" to ProgramManager""" in {
        DartGroup.fromString( "program-manager" ) shouldBe ProgramManager
    }

    it should """convert "global/[role]" to TenantGroup(Global, [Role])""" in {
        DartGroup.fromString( "global/leader" ) shouldBe TenantGroup( GlobalCorpus, Leader )
        DartGroup.fromString( "global/member" ) shouldBe TenantGroup( GlobalCorpus, Member )
        DartGroup.fromString( "global/read-only" ) shouldBe TenantGroup( GlobalCorpus, ReadOnly )
    }

    it should """convert "baltics/[role] to TenantGroup(CorpusTenant("baltics", GlobalCorpus), [Role])""" in {
        DartGroup.fromString( "baltics/leader" ) shouldBe TenantGroup( CorpusTenant( "baltics", GlobalCorpus ), Leader )
        DartGroup.fromString( "baltics/member" ) shouldBe TenantGroup( CorpusTenant( "baltics", GlobalCorpus ), Member )
        DartGroup.fromString( "baltics/read-only" ) shouldBe TenantGroup( CorpusTenant( "baltics", GlobalCorpus ), ReadOnly )
    }

    it should """convert "baltics-23/[role] to TenantGroup(CorpusTenant("baltics-23", GlobalCorpus), [Role])""" in {
        DartGroup.fromString( "baltics-23/leader" ) shouldBe TenantGroup( CorpusTenant( "baltics-23", GlobalCorpus ), Leader )
        DartGroup.fromString( "baltics-23/member" ) shouldBe TenantGroup( CorpusTenant( "baltics-23", GlobalCorpus ), Member )
        DartGroup.fromString( "baltics-23/read-only" ) shouldBe TenantGroup( CorpusTenant( "baltics-23", GlobalCorpus ), ReadOnly )
    }

    it should """throw an IllegalArgumentException when corpus id contains characters other than letters, numbers, or dash""" in {
        an [IllegalArgumentException] should be thrownBy( DartGroup.fromString( "baltics%/leader" ) )
        an [IllegalArgumentException] should be thrownBy( DartGroup.fromString( "baltics%/member" ) )
        an [IllegalArgumentException] should be thrownBy( DartGroup.fromString( "baltics%/read-only" ) )
    }

    it should """throw an IllegalArgumentException when role is invalid""" in {
        an [IllegalArgumentException] should be thrownBy( DartGroup.fromString( "global/sdfsasdf" ) )
        an [IllegalArgumentException] should be thrownBy( DartGroup.fromString( "baltics/sdfsasdf" ) )
    }

    it should """always generate the same group from that group's toString output""" in {
        DartGroup.fromString( ProgramManager.toString ) shouldBe ProgramManager
        DartGroup.fromString( TenantGroup( GlobalCorpus, Leader ).toString ) shouldBe TenantGroup( GlobalCorpus, Leader )
        DartGroup.fromString( TenantGroup( GlobalCorpus, Member ).toString ) shouldBe TenantGroup( GlobalCorpus, Member )
        DartGroup.fromString( TenantGroup( GlobalCorpus, ReadOnly ).toString ) shouldBe TenantGroup( GlobalCorpus, ReadOnly )
        DartGroup.fromString( TenantGroup( CorpusTenant( "baltics" ), Leader ).toString ) shouldBe TenantGroup( CorpusTenant( "baltics" ), Leader )
        DartGroup.fromString( TenantGroup( CorpusTenant( "baltics" ), Member ).toString ) shouldBe TenantGroup( CorpusTenant( "baltics" ), Member )
        DartGroup.fromString( TenantGroup( CorpusTenant( "baltics" ), ReadOnly ).toString ) shouldBe TenantGroup( CorpusTenant( "baltics" ), ReadOnly )
        DartGroup.fromString( TenantGroup( CorpusTenant( "baltics-23" ), Leader ).toString ) shouldBe TenantGroup( CorpusTenant( "baltics-23" ), Leader )
        DartGroup.fromString( TenantGroup( CorpusTenant( "baltics-23" ), Member ).toString ) shouldBe TenantGroup( CorpusTenant( "baltics-23" ), Member )
        DartGroup.fromString( TenantGroup( CorpusTenant( "baltics-23" ), ReadOnly ).toString ) shouldBe TenantGroup( CorpusTenant( "baltics-23" ), ReadOnly )
    }

}
