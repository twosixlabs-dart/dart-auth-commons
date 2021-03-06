import sbt._
import Dependencies._

// integrationConfig and wipConfig are used to define separate test configurations for integration testing
// and work-in-progress testing
lazy val IntegrationConfig = config( "integration" ) extend( Test )
lazy val WipConfig = config( "wip" ) extend( Test )

lazy val commonJvmSettings = Seq(
    dependencyOverrides ++= Seq( "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.10.5",
        "com.arangodb" %% "velocypack-module-scala" % "1.2.0",
        "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.10.5",
        "com.fasterxml.jackson.core" % "jackson-databind" % "2.10.5" ),
    libraryDependencies ++= Seq(
        logging,
        jackson,
        dartCommons,
        cdr4s,
        betterFiles,
        typesafeConfig,
    ).flatten,
)

lazy val commonJsSettings = Seq(

)

lazy val commonSettings : Seq[ Def.Setting[ _ ] ] = {
    inConfig( IntegrationConfig )( Defaults.testTasks ) ++
    inConfig( WipConfig )( Defaults.testTasks ) ++
    Seq(
        organization := "com.twosixlabs.dart.auth",
        scalaVersion in ThisBuild := "2.12.13",
        resolvers in ThisBuild ++= Seq(
            "Maven Central" at "https://repo1.maven.org/maven2/",
            "JCenter" at "https://jcenter.bintray.com",
            "Local Ivy Repository" at s"file://${System.getProperty( "user.home" )}/.ivy2/local/default"

        ),
        libraryDependencies ++= Seq(
            scalaTest.value,
            scalaMock.value,
            scalaRbac.value,
        ).flatten,
        // `sbt test` should skip tests tagged IntegrationTest
        Test / testOptions := Seq( Tests.Argument( "-l", "annotations.IntegrationTest" ) ),
        // `sbt integration:test` should run only tests tagged IntegrationTest
        IntegrationConfig / parallelExecution := false,
        IntegrationConfig / testOptions := Seq( Tests.Argument( "-n", "annotations.IntegrationTest" ) ),
        // `sbt wip:test` should run only tests tagged WipTest
        WipConfig / testOptions := Seq( Tests.Argument( "-n", "annotations.WipTest" ) ),
    )
}

lazy val disablePublish = Seq(
    skip.in( publish ) := true,
    )

lazy val root = ( project in file( "." ) )
  .settings( name := "dart-auth-commons", disablePublish )
  .aggregate(
      core.projects( JSPlatform ),
      core.projects( JVMPlatform ),
      controllers,
      keycloakTenants,
      arangoTenants,
      keycloakUsers,
      keycloakCommon,
  )

lazy val core = ( crossProject(JSPlatform, JVMPlatform) in file( "modules/core" ) )
  .configs( WipConfig, IntegrationConfig )
  .settings( commonSettings )
  .jvmSettings(
      commonJvmSettings,
  )
  .jsSettings(
      commonJsSettings,
      libraryDependencies ++= dartExceptions,
  )

lazy val controllers = ( project in file( "modules/controllers" ) )
  .configs( WipConfig, IntegrationConfig )
  .dependsOn( core.projects( JVMPlatform ) )
  .settings(
      commonSettings,
      commonJvmSettings,
      libraryDependencies ++= Seq(
          scalaJwt,
          scalatra,
      ).flatten,
  )

lazy val keycloakCommon = ( project in file( "modules/keycloak-common" ) )
  .configs( WipConfig, IntegrationConfig )
  .settings(
      name := "keycloak-common",
      commonSettings,
      commonJvmSettings,
      libraryDependencies ++= Seq(
          keycloak4s,
      ).flatten,
  )

lazy val keycloakTenants = ( project in file( "modules/tenant-index/keycloak-tenants" ) )
  .configs( WipConfig, IntegrationConfig )
  .dependsOn( core.projects( JVMPlatform ) % "compile->compile;test->test", keycloakCommon )
  .settings(
      name := "keycloak-tenants",
      commonSettings,
      commonJvmSettings,
      libraryDependencies ++= Seq(
          keycloak4s,
      ).flatten,
  )

lazy val arangoTenants = ( project in file( "modules/tenant-index/arango-tenants" ) )
  .configs( WipConfig, IntegrationConfig )
  .dependsOn( core.projects( JVMPlatform ) % "compile->compile;test->test" )
  .settings(
      commonSettings,
      commonJvmSettings,
      libraryDependencies ++= Seq(
          arangoDatastoreRepo,
      ).flatten,
  )

lazy val keycloakUsers = ( project in file( "modules/user-store/keycloak-users" ) )
  .configs( WipConfig, IntegrationConfig )
  .dependsOn( core.projects( JVMPlatform ) % "compile->compile;test->test", keycloakTenants, keycloakCommon )
  .settings(
      commonSettings,
      commonJvmSettings,
      libraryDependencies ++= Seq(
          keycloak4s,
      ).flatten,
  )

sonatypeProfileName := "com.twosixlabs"
inThisBuild(List(
    organization := "com.twosixlabs.dart.auth",
    homepage := Some(url("https://github.com/twosixlabs-dart/dart-auth-commons")),
    licenses := List("GNU-Affero-3.0" -> url("https://www.gnu.org/licenses/agpl-3.0.en.html")),
    developers := List(
        Developer(
            "twosixlabs-dart",
            "Two Six Technologies",
            "",
            url("https://github.com/twosixlabs-dart")
            )
        )
    ))

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

