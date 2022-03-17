package com.twosixlabs.dart.auth.tenant.indices

import better.files.Resource
import com.datastax.driver.core.SimpleStatement
import com.twosixlabs.dart.auth.tenant.CorpusTenantIndexTest
import com.twosixlabs.dart.cassandra.{Cassandra, CassandraConf}
import com.twosixlabs.dart.test.tags.annotations.IntegrationTest
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Future

object CassandraTestHelper {

    def parseCqlFileCommands( filename : String ) : List[ String ] = {
        Resource.getAsString( filename ).split( ";" ).toList.filter( stmt => stmt != null && stmt.trim().nonEmpty )
    }

    def executeCommands( cassandraConf : CassandraConf, commands : List[ String ] ) : Unit = {
        val cassandra = new Cassandra( cassandraConf )
        commands
          .map( cmd => new SimpleStatement( cmd ) )
          .foreach( cassandra.session.execute( _ ) )

        cassandra.session.close()
    }

}

@IntegrationTest
class CassandraCorpusTenantIndexTest extends CorpusTenantIndexTest(
    CassandraCorpusTenantIndex(
        CassandraConf(
            hosts = List( "localhost" ),
            port = 9042,
            keyspace = Some( "dart" )
        )
    ),
    5000,
    500,
) with BeforeAndAfterAll {

    override def beforeEach( ) : Unit = {
        index.allTenants.flatMap( tenants => Future.sequence( tenants.map( v => index.removeTenant( v ) ) ) ).awaitWrite
        super.beforeEach()
    }

//    override def beforeAll( ) : Unit = {
//        val setup = CassandraTestHelper.parseCqlFileCommands( "cql/setup.cql" )
//        CassandraTestHelper.executeCommands( CassandraConf(
//            hosts = List( "localhost" ),
//            port = 9042,
//            keyspace = Some( "dart" )
//            ), setup )
//        super.beforeAll()
//    }
//
//    override def afterAll( ) : Unit = {
//        super.afterAll()
//        val cleanup = CassandraTestHelper.parseCqlFileCommands( "cql/cleanup.cql" )
//        CassandraTestHelper.executeCommands( CassandraConf(
//            hosts = List( "localhost" ),
//            port = 9042,
//            keyspace = Some( "dart" )
//            ), cleanup )
//    }
}
