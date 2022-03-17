package com.twosixlabs.dart.auth.user

import com.twosixlabs.dart.auth.groups.DartGroup
import com.twosixlabs.dart.auth.permissions.DartGroupRole
import org.hungerford.rbac.{NoRole, Role, Roles, User}

case class DartUser(
    userName : String,
    groups : Set[ DartGroup ],
    userInfo : DartUserInfo = DartUserInfo(),
) extends User {
    override val roles : Role = {
        if ( groups.nonEmpty ) Role.join( groups.map( DartGroupRole ) )
        else NoRole
    }

    lazy val formattedRoles : String = roles match {
        case rs : Roles => rs.roles.mkString( ", " )
        case r : Role => r.toString
    }

    override def toString : String = s"Dart User: ${userName}; roles: ${roles}"
}

case class DartUserInfo(
    firstName : Option[ String ] = None,
    lastName : Option[ String ] = None,
    email : Option[ String ] = None,
)
