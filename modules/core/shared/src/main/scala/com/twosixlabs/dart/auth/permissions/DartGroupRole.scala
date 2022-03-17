package com.twosixlabs.dart.auth.permissions

import com.twosixlabs.dart.auth.groups.DartGroup
import org.hungerford.rbac.{Permission, Role}

case class DartGroupRole( dartGroup : DartGroup ) extends Role {
    override val permissions : Permission = DartPermissions.getPermissionsFromGroup( dartGroup )

    override def toString : String = dartGroup.toString
}
