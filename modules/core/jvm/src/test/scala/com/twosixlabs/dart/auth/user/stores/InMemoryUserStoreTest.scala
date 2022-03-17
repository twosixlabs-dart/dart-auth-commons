package com.twosixlabs.dart.auth.user.stores

import com.twosixlabs.dart.auth.tenant.indices.InMemoryCorpusTenantIndex
import com.twosixlabs.dart.auth.user.DartUserStoreTest

class InMemoryUserStoreTest extends DartUserStoreTest( new InMemoryUserStore, new InMemoryCorpusTenantIndex )
