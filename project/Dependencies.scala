import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._


object Dependencies {

    val typesafeConfigVersion = "1.4.1"
    val slf4jVersion = "1.7.20"
    val logbackVersion = "1.2.3"
    val dartCommonsVersion = "3.0.30"
    val cdr4sVersion = "3.0.9"
    val scalaTestVersion = "3.2.9"
    val scalaMockVersion = "5.1.0"
    val scalatraVersion = "2.5.4"
    val jacksonVersion = "2.9.9"
    val jacksonOverrideVersion = "2.9.10"
    val jwtScalaVersion = "5.0.0"
    val scalaRbacVersion = "1.1.0"
    val arangoDatastoreRepoVersion = "3.0.8"
    val betterFilesVersion = "3.8.0"
    val keycloak4sVersion = "2.4.1"
    val akkaMonixTaskVersion = "1.5.0"

    val typesafeConfig = Seq( "com.typesafe" % "config" % typesafeConfigVersion )

    val betterFiles = Seq( "com.github.pathikrit" %% "better-files" % betterFilesVersion % Test )

    val logging = Seq( "org.slf4j" % "slf4j-api" % slf4jVersion,
                       "ch.qos.logback" % "logback-classic" % logbackVersion )

    val dartExceptions = Seq( "com.twosixlabs.dart" %% "dart-exceptions" % dartCommonsVersion )
    val dartCommons = Seq( "com.twosixlabs.dart" %% "dart-test-base" % dartCommonsVersion % "test",
                           "com.twosixlabs.dart" %% "dart-json" % dartCommonsVersion ) ++ dartExceptions

    val cdr4s = Seq( "com.twosixlabs.cdr4s" %% "cdr4s-core" % cdr4sVersion )

    val scalatra = Seq( "org.scalatra" %% "scalatra" % scalatraVersion,
                        "org.scalatra" %% "scalatra-scalate" % scalatraVersion,
                        "org.scalatra" %% "scalatra-scalatest" % scalatraVersion % "test" )

    val scalaTest = Def.setting( Seq( "org.scalatest" %%% "scalatest" % scalaTestVersion % "test" ) )
    val scalaMock = Def.setting( Seq( "org.scalamock" %%% "scalamock" % scalaMockVersion % "test" ) )

    val jackson = Seq( "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
                       "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion )

    val scalaJwt = Seq( "com.pauldijou" %% "jwt-core" % jwtScalaVersion )

    val scalaRbac = Def.setting( Seq( "io.github.johnhungerford.rbac" %%% "scala-rbac-core" % scalaRbacVersion,
                                 "io.github.johnhungerford.rbac" %% "scala-rbac-scalatra" % scalaRbacVersion ) )

    val arangoDatastoreRepo = Seq( "com.twosixlabs.dart" %% "dart-arangodb-datastore" % arangoDatastoreRepoVersion )

    val keycloak4s = Seq( "com.fullfacing" %% "keycloak4s-core" % keycloak4sVersion,
                          "com.fullfacing" %% "keycloak4s-admin-monix" % keycloak4sVersion,
                          "com.fullfacing" %% "sttp-akka-monix-task" % akkaMonixTaskVersion )
}
