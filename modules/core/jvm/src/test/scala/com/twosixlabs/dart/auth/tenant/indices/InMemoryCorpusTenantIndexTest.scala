package com.twosixlabs.dart.auth.tenant.indices

import com.twosixlabs.dart.auth.tenant.CorpusTenantIndexTest

import scala.concurrent.Future

class InMemoryCorpusTenantIndexTest extends CorpusTenantIndexTest( new InMemoryCorpusTenantIndex, writeDelayMs = 500 ) {
    override def beforeEach( ) : Unit = {
        index.allTenants.flatMap( tenants => Future.sequence( tenants.map( v => index.removeTenant( v ) ) ) ).awaitWrite
        super.beforeEach()
    }
}
