package com.twosixlabs.dart.auth.permissions

import com.twosixlabs.dart.auth.groups.DartGroup
import com.twosixlabs.dart.auth.permissions.DartOperations.AddDocument.on
import com.twosixlabs.dart.auth.tenant.DartTenant
import com.twosixlabs.dart.exceptions.AuthorizationException
import org.hungerford.rbac.exceptions.{AuthorizationException => RbacAuthorizationException}
import org.hungerford.rbac.{NoPermissions, Permissible, PermissibleSet, Permission, PermissionManagement, PermissionOperation, PermissionSource}

import scala.util.{Failure, Success, Try}

object DartOperations {
    sealed trait DartOperation extends Permissible {

        def secureDart[ T ]( block : => T )( implicit ps : PermissionSource ) : T = Try( secure( block ) ) match {
            case Success( res ) => res
            case Failure( e : RbacAuthorizationException ) => throw new AuthorizationException( e.getMessage )
            case Failure( e ) => throw e
        }

        def trySecureDart[ T ]( handler : Option[ Throwable ] => T )( implicit ps : PermissionSource ) : T = trySecure { eOpt =>
            handler( eOpt match {
                case None => None
                case Some( e : RbacAuthorizationException ) => Some( new AuthorizationException( e.getMessage ) )
                case Some( e ) => Some( e )
            } )
        }

    }

    // Simple operations

    sealed trait OperationOnTenant extends DartOperation {
        def on( tenant : DartTenant ) : TenantOperation = TenantOperation( tenant, this )
    }

    case object ViewTenant extends OperationOnTenant {
        def apply( tenant : DartTenant ) : TenantOperation = on( tenant )
    }

    case object AddDocument extends OperationOnTenant {
        def to( tenant : DartTenant ) : TenantOperation = on( tenant )
    }
    case object RemoveDocument extends OperationOnTenant {
        def from( tenant : DartTenant ) : TenantOperation = on( tenant )
    }
    case object RetrieveDocument extends OperationOnTenant  {
        def from( tenant : DartTenant ) : TenantOperation = on( tenant )
    }
    case object UpdateDocument extends OperationOnTenant {
        def in( tenant : DartTenant ) : TenantOperation = on( tenant )
    }
    case object SearchCorpus extends OperationOnTenant  {
        def apply( tenant : DartTenant ) : TenantOperation = on( tenant )
    }

    sealed trait TenantManagementOperation extends OperationOnTenant
    case object RetrieveTenant extends TenantManagementOperation {
        def from( tenant : DartTenant ) : TenantOperation = on( tenant )
    }
    case object CreateTenant extends TenantManagementOperation  {
        def in( tenant : DartTenant ) : TenantOperation = on( tenant )
    }
    case object DeleteTenant extends TenantManagementOperation  {
        def from( tenant : DartTenant ) : TenantOperation = on( tenant )
    }

    sealed trait UserOperation extends PermissionOperation with DartOperation {
        def atLevel( groups : Iterable[ DartGroup ] ) : PermissibleSet = UserOperation( groups.toSet, this )
        def atLevel( groups : DartGroup* ) : PermissibleSet = atLevel( groups )
    }

    case object RetrieveUser extends UserOperation
    case object AddUser extends UserOperation
    case object DeleteUser extends UserOperation
    case object UpdateUserRole extends UserOperation
    case object UpdateUserInfo extends UserOperation


    // Complex operations: simple operations relative to some dart type

    case class TenantOperation( tenant : DartTenant, operation : OperationOnTenant ) extends DartOperation {
        override def toString : String = s"$operation on tenant: $tenant"
    }

    def UserOperation( groups : Set[ DartGroup ], op : UserOperation ) : PermissibleSet = {
        Permissible.all( groups.map( g => new PermissionManagement( Permission( DartPermissions.getPermissionsFromGroup( g ) ), op ) with DartOperation ) )
    }

}
