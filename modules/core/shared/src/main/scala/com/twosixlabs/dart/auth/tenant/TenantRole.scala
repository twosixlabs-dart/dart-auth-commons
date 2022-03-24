package com.twosixlabs.dart.auth.tenant

import com.twosixlabs.dart.auth.permissions.DartOperations._
import org.hungerford.rbac.Permission

sealed trait TenantRole {
    val permittedOperations : Set[ OperationOnTenant ]

    def tenantPermissions : Permission = Permission.to( permittedOperations )
}

case object Leader extends TenantRole {
    override val permittedOperations : Set[ OperationOnTenant ] = Set(
        ViewTenant, AddDocument, RemoveDocument, RetrieveDocument, UpdateDocument, SearchCorpus, RetrieveTenant
    )

    override def toString : String = "leader"
}

case object Member extends TenantRole {
    override val permittedOperations : Set[ OperationOnTenant ] = Set(
        ViewTenant, AddDocument, RemoveDocument, RetrieveDocument, UpdateDocument, SearchCorpus, RetrieveTenant
    )

    override def toString : String = "member"
}

case object ReadOnly extends TenantRole {
    override val permittedOperations : Set[ OperationOnTenant ] = Set(
        ViewTenant, RetrieveDocument, SearchCorpus, RetrieveTenant
    )

    override def toString : String = "read-only"
}
