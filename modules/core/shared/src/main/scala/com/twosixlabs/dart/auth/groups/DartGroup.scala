package com.twosixlabs.dart.auth.groups

import com.twosixlabs.dart.auth.tenant.{CorpusTenant, DartTenant, GlobalCorpus, Leader, Member, ReadOnly, TenantRole}

import scala.util.matching.Regex

sealed trait DartGroup

case object ProgramManager extends DartGroup {
    override def toString : String = "program-manager"
}

case class TenantGroup( tenant : DartTenant, tenantRole : TenantRole ) extends DartGroup {
    lazy val tenantSlug : String = tenant match {
        case GlobalCorpus => DartTenant.globalId
        case ct : CorpusTenant => ct.id
    }
    override def toString : String = s"$tenantSlug/$tenantRole"
}

object DartGroup {
    val ProgramManagerPattern : Regex = """program-manager""".r
    val GroupPattern : Regex = """([a-z0-9\-]+)/([a-z\-]+)""".r
    val GlobalCorpusPattern : Regex = """global""".r
    val ReadOnlyPattern : Regex = """read-only""".r
    val LeaderPattern : Regex = """leader""".r
    val MemberPattern : Regex = """member""".r

    def fromString( groupName : String ) : DartGroup = {
        groupName match {
            case ProgramManagerPattern() =>
                ProgramManager
            case GroupPattern( corpusName, roleName ) =>
                val tenant = corpusName match {
                    case GlobalCorpusPattern() => GlobalCorpus
                    case other => CorpusTenant( other )
                }
                val role = roleName match {
                    case LeaderPattern() => Leader
                    case MemberPattern() => Member
                    case ReadOnlyPattern() => ReadOnly
                    case _ => throw new IllegalArgumentException( s"$roleName is not a valid role name" )
                }
                TenantGroup( tenant, role )
            case _ => throw new IllegalArgumentException( s"$groupName is not a valid group name" )
        }
    }
}
