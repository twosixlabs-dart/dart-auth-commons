package com.twosixlabs.dart.auth.tenant.indices

import com.arangodb.async.ArangoCollectionAsync
import com.twosixlabs.dart.arangodb.{Arango, ArangoConf}
import com.twosixlabs.dart.auth.tenant.CorpusTenantIndexTest
import com.twosixlabs.dart.test.tags.annotations.IntegrationTest
import org.scalatest.BeforeAndAfterAll

@IntegrationTest
class ArangoCorpusTenantIndexTest extends CorpusTenantIndexTest(
    ArangoCorpusTenantIndex(
        ArangoConf(
            host = Option( System.getenv( "ARANGODB_HOST" ) ).getOrElse( "localhost" ),
            port = 8529,
            database = "dart"
            )
        ),
    5000,
    500,
    ) with BeforeAndAfterAll {
    protected val arangoConf : ArangoConf = ArangoConf( host = Option( System.getenv( "ARANGODB_HOST" ) ).getOrElse( "localhost" ),
                                                        port = 8529, database = "dart" )
    protected val arango = new Arango( arangoConf )
    private val COLLECTION_NAME : String = "tenant_docs"
    protected val collection : ArangoCollectionAsync = arango.collection( COLLECTION_NAME )

    override def beforeAll( ) : Unit = {
        collection.truncate().get()
        super.beforeAll()
    }

    override def afterEach( ) : Unit = {
        collection.truncate().get()
        super.afterEach()
    }

    override def beforeEach( ) : Unit = {
        collection.truncate().get()
        super.beforeEach()
    }
}
