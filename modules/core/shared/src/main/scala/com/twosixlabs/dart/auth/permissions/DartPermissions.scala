package com.twosixlabs.dart.auth.permissions

import com.twosixlabs.dart.auth.groups.{DartGroup, ProgramManager, TenantGroup}
import com.twosixlabs.dart.auth.permissions.DartOperations._
import com.twosixlabs.dart.auth.tenant.{Leader, Member, ReadOnly, TenantRole}
import org.hungerford.rbac._

object DartPermissions {
    case class TenantOperationPermission( group : TenantGroup ) extends SimplePermission {
        override def permits( permissible : Permissible ) : Boolean = group match {
            case TenantGroup( tenant, role : TenantRole ) => permissible match {
                case TenantOperation( opTenant , op ) if tenant == opTenant => role.tenantPermissions.permits( op )
                case _ => false
            }
        }

        override def tryCompareTo[ B >: Permission ]( that : B )( implicit evidence : B => PartiallyOrdered[ B ] ) : Option[ Int ] = {
            group match {
                case TenantGroup( thisTenant, thisRole ) =>
                    that match {
                        case TenantOperationPermission( thatGroup ) => thatGroup match {
                            case TenantGroup( thatTenant, thatRole ) =>
                                if ( thisTenant == thatTenant ) thisRole.tenantPermissions.tryCompareTo( thatRole.tenantPermissions )
                                else None
                            case _ => None
                        }
                        case _ => super.tryCompareTo( that )
                    }
                case _ => super.tryCompareTo( that )
            }
        }
    }

    def getUserOperationPermissions( group : TenantGroup, tenantPermissions : Permission ) : Permission = group match {
        case TenantGroup( _, ReadOnly ) => RecursivePermissionManagementPermission( tenantPermissions, Permission.to( RetrieveUser ) )
        case TenantGroup( _, Member ) => RecursivePermissionManagementPermission( tenantPermissions, Permission.to( RetrieveUser ) )
        case TenantGroup( _, Leader ) => RecursivePermissionManagementPermission(
            tenantPermissions,
            Permission.to( RetrieveUser, AddUser, DeleteUser, UpdateUserRole, UpdateUserInfo ),
        )
    }

    def getPermissionsFromGroup( group : DartGroup ) : Permission = group match {
        case ProgramManager => AllPermissions
        case tg@TenantGroup( _, _ ) =>
            val tenantPermissions = TenantOperationPermission( tg )
            tenantPermissions | getUserOperationPermissions( tg, tenantPermissions )
    }
}
