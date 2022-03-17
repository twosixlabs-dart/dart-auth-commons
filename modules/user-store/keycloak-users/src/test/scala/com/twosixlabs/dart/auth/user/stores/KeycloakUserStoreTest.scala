package com.twosixlabs.dart.auth.user.stores

import com.twosixlabs.dart.auth.keycloak.KeycloakAdminClient
import com.twosixlabs.dart.auth.tenant.indices.KeycloakCorpusTenantIndex
import com.twosixlabs.dart.auth.user.DartUserStoreTest
import com.twosixlabs.dart.test.tags.annotations.IntegrationTest

object KeycloakContainer {
    val keycloakAdminClient = KeycloakAdminClient(
        "http",
        "localhost",
        8090,
        realm = "dart",
        adminRealm = "dart",
        adminClientId = "dart-admin",
        adminClientSecret = "a5dd2106-326b-4b2b-bf0a-c4afd5e86851",
    ),
}

@IntegrationTest
class KeycloakUserStoreTest extends DartUserStoreTest(
    KeycloakUserStore( KeycloakContainer.keycloakAdminClient ),
    KeycloakCorpusTenantIndex( KeycloakContainer.keycloakAdminClient ),
)
