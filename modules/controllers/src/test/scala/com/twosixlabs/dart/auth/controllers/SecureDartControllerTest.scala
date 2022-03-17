package com.twosixlabs.dart.auth.controllers

import com.twosixlabs.dart.auth.groups.{ProgramManager, TenantGroup}
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, GlobalCorpus, Member}
import com.twosixlabs.dart.auth.user.DartUser
import com.twosixlabs.dart.exceptions.AuthenticationException
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import pdi.jwt.{Jwt, JwtAlgorithm}

class TokenGenerator( key : String ) {
    def currentTimeSeconds : Int = Math.round( System.currentTimeMillis() / 1000 )

    def genUserClaim( userName : String, groups : List[ String ] ) : String = {
        val ct = currentTimeSeconds
        val groupsJson = groups.map( s => "\"" + s + "\"" ).mkString(", ")
        s"""{"exp": ${ct + 3000},"iat": ${ct},"auth_time": 1614621851,"jti": "8da0a6ea-e36a-4811-8eee-2d5db677b73e","iss": "http://localhost:8090/auth/realms/dart","aud": ["forklift","corpex","tenants"],"sub": "3a368a9c-7f21-42f7-bbe6-f2e9c4754670","typ": "Bearer","azp": "dart-ui","nonce": "50c28985-ba20-4923-8fbf-313f6b00ebcb","session_state": "3f8762a6-1eb0-467c-9450-0f573987cce1","acr": "1","allowed-origins": ["http://localhost:8080"],"scope": "openid email profile","email_verified": false,"preferred_username": "${userName}","group": [${groupsJson}]}""".stripMargin
    }

    def apply( userName : String, groups : List[ String ] ) : String = Jwt.encode( genUserClaim( userName, groups ), key, JwtAlgorithm.RS256 )

}

class SecureDartControllerTest extends AnyFlatSpecLike with Matchers  {

    object SecureBaseControllerImpl extends SecureDartController {
        override val serviceName : String = "corpex"
        override val useDartAuth : Boolean = true
        override val basicAuthCredentials: Seq[ (String, String) ] = Seq( "test-ba-user" -> "test-ba-pwd" )
        override val secretKey : Option[ String ] = Some( "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsWoUaYaoquEP88g0s9Dd\naCI7L7yIQPfaycjgNcnUEmmOSsWY0WS5ZPwnPUE8Ubw9cUcqxXJU2NYI1RUOA4iY\n9zj7FUs8HKE6J+xnyviDH/a1V8JqqSyAD0FycxE9EcIRol31aCRYLTBWMTm1U8xF\nxvqhclYiJCAsjzWkiiFI104aCZ+rm99XBYkkBYk2/F1DBXrgLUF/1uLkf4ic62EQ\npDX95cJo92qTfClGCpdhF/nM2B2QIPY8DJvyWdaPu3CUs5Ilv/3rzljtPvE3n45n\nW5Jz9XsrdgO/5mtZNxERiQo4HMRMgWiRjKhNjyPQINm/d0CLPK3KMgz0VXMzf5IZ\nfQIDAQAB" )
    }

    val GenerateToken = new TokenGenerator( "MIIEuwIBADANBgkqhkiG9w0BAQEFAASCBKUwggShAgEAAoIBAQCxahRphqiq4Q/z\nyDSz0N1oIjsvvIhA99rJyOA1ydQSaY5KxZjRZLlk/Cc9QTxRvD1xRyrFclTY1gjV\nFQ4DiJj3OPsVSzwcoTon7GfK+IMf9rVXwmqpLIAPQXJzET0RwhGiXfVoJFgtMFYx\nObVTzEXG+qFyViIkICyPNaSKIUjXThoJn6ub31cFiSQFiTb8XUMFeuAtQX/W4uR/\niJzrYRCkNf3lwmj3apN8KUYKl2EX+czYHZAg9jwMm/JZ1o+7cJSzkiW//evOWO0+\n8TefjmdbknP1eyt2A7/ma1k3ERGJCjgcxEyBaJGMqE2PI9Ag2b93QIs8rcoyDPRV\nczN/khl9AgMBAAECggEAIuHWCJeC0zI0Fjzva8lttttAItchPRDech0uRDUeHs6l\nPPtT3Bu/PeU7+9W3O+TUedDBzpd3qjiO/QJKQpSZasZHy7yCoahrjEz1bVlmQqMZ\nrZsaqI+I0DcQfyJNDRBIslFh/mti8OOXls8fYU4BDCncE2RvCwZObIviAYygy0AK\nmx+68vSTICExupGfRCkoRgA++UYuchIT5n5XLnUd/07TsNbRsBHiGmMEU3S+ChmA\nTFUk05TADKKxVNxs0JYFPcxkc0YddRgcS5z8r2xv/CdnYVTIh2oWANIy8KpOAazX\nJrdyP/16hfcipa5Vmo2rM7SGLz8idySSu9ynSjl5sQKBgQDYazAjpN2GtXCNPKpq\nu+J12wsFlRRm6rXX+KCGQcD755Ju69V/+3gpoa+kZvgJCQRvbYr4/2U4kbabpD0u\nD1aL4e3kcYhL9bSff4AwbiNtAVx3BkFNw3ovoC0NSIZuIPwsS/RyekrR6KBMU9Kx\n2farWFDPtKVgRBwp+jJz5Mc/tQKBgQDR3LFt8ekgg1jphEqXVGh5o/mGWMbL2jIT\ncHtIJWcNhsrGzNEJJsoRy9Sej20MzeRrjiBUlMZ+ZrnKTl+fsp8W0as0Jqn65NbH\nXGrVYKjtDKa0VbA33wIM51zyV8sp8rtK2nxAMlU82HU5F52fHlenoMjavM3SKF6Z\ndFPjOB2/qQKBgE4sgcoOTnrPZWWPKVu+nHHH+rY8gI8gbykhRRLaA5S6tFle1NMT\nCfe30NNI4oWY/UImxpFk57JEaDrWW8ccgT2sEZt4KyyNB36ptqtHzYIdgMH5v3MQ\njj1P1ZY7wVtcjNqPYTBM3mPubKDFkpDt071W/HHapfYdBDNhjgHw/MwVAoGBAK1C\n0m3eCkWgi1iHsvmLY5IB0fGb+8yzgSJRfEbNMm6VeXZ2BKLUloHo0cpyyWqH1e0C\ncyjBh7s13m/rEsGLPBMf2YP949pS8gu3/i/aVL0Y88zw7cLC6DN1FGS0HPSuBPlA\nttJde8f3QZh4KdcDuwSXFxyYQike0TNKOjPe0Zi5An9DEGR/MPoCgg7twWJQvX7K\nuBAesgXllLNz0HZG8vYFhG02jEEnI9lnWnO1+qvzy6NBQEckm35nWNEikh77oXJm\nE/GSK6K8rkvkQGV7VMfBMT0e/0Q5bWz0U23pn4ksiMPTnrGPOuaEgsKlhB8KgFok\niVgUuHuOrTY+cnGhfx/E" )

    behavior of "SecureDartController"

    it should "decode JWT token and parse groups" in {
        val token = GenerateToken( "test-member", List( "/baltics/member" ) )
        val bearerToken = s"Bearer $token"
        SecureBaseControllerImpl.authenticateUser( bearerToken ) shouldBe DartUser( "test-member", Set( TenantGroup( CorpusTenant( "baltics", GlobalCorpus ), Member ) ) )
    }

    it should "decode basic auth as program-manager if basic auth credentials are passed" in {
        SecureBaseControllerImpl.authenticateUser( "Basic dGVzdC1iYS11c2VyOnRlc3QtYmEtcHdk" ) shouldBe DartUser( "basic-auth", Set( ProgramManager ) )
    }

    it should "throw AuthenticationFailure exception is basic auth creds are wrong" in {
        a [ AuthenticationException ] should be thrownBy SecureBaseControllerImpl.authenticateUser( "Basic d3JvbmctdW46d3JvbmctcHdk" )
    }

//    "SecureBaseController" should "decode JWT token and return an empty group list when groups field is missing" in {
//        val bearerToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MTM2NzczMzQsImlhdCI6MTYxMzY3NzAzNCwiYXV0aF90aW1lIjoxNjEzNjc3MDMyLCJqdGkiOiJiMGViYzc5YS1hYjM2LTRhZjgtOGUwMy0wZTc0YTY2MjI3NjUiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwOTAvYXV0aC9yZWFsbXMvZGFydCIsImF1ZCI6WyJmb3JrbGlmdCIsImNvcnBleCJdLCJzdWIiOiIzYTM2OGE5Yy03ZjIxLTQyZjctYmJlNi1mMmU5YzQ3NTQ2NzAiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJkYXJ0LXVpIiwibm9uY2UiOiIxZTlhNGQ5Mi1lNTZiLTQzZjctODZiZC05ZGY0MjZiN2MwMGQiLCJzZXNzaW9uX3N0YXRlIjoiYzg2MzAwYzAtYTdhMC00NmRhLWIyNTEtYjcwYjdmZmU3ZmU3IiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vbG9jYWxob3N0OjgwODEiXSwic2NvcGUiOiJvcGVuaWQgZW1haWwgcHJvZmlsZSIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuYW1lIjoidGVzdC1tZW1iZXIiLCJ0ZWFtIjoidHdvc2l4In0.6uhw-69VlPuSsonRFPOGRynOwTc4h48g1bnwDbVrEDY"
//        SecureBaseControllerImpl.authenticateUser( bearerToken ) shouldBe DartUser("", Set("test-member") )
//    }
//
//    "SecureBaseController" should "decode JWT token and return an empty group list when groups are in a wrong format" in {
//        val bearerToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MTM2NzczMzQsImlhdCI6MTYxMzY3NzAzNCwiYXV0aF90aW1lIjoxNjEzNjc3MDMyLCJqdGkiOiJiMGViYzc5YS1hYjM2LTRhZjgtOGUwMy0wZTc0YTY2MjI3NjUiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwOTAvYXV0aC9yZWFsbXMvZGFydCIsImF1ZCI6WyJmb3JrbGlmdCIsImNvcnBleCJdLCJzdWIiOiIzYTM2OGE5Yy03ZjIxLTQyZjctYmJlNi1mMmU5YzQ3NTQ2NzAiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJkYXJ0LXVpIiwibm9uY2UiOiIxZTlhNGQ5Mi1lNTZiLTQzZjctODZiZC05ZGY0MjZiN2MwMGQiLCJzZXNzaW9uX3N0YXRlIjoiYzg2MzAwYzAtYTdhMC00NmRhLWIyNTEtYjcwYjdmZmU3ZmU3IiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vbG9jYWxob3N0OjgwODEiXSwic2NvcGUiOiJvcGVuaWQgZW1haWwgcHJvZmlsZSIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuYW1lIjoidGVzdC1tZW1iZXIiLCJ0ZWFtIjoidHdvc2l4IiwiZ3JvdXAiOlsiL2JhbHRpY3MiXX0.slsGsTsevrXJsQLTv4L6Titna5nKKS5kVjBO7Nq8UxY"
//        SecureBaseControllerImpl.authenticateUser( bearerToken ) shouldBe DartUser("", Set("test-member") )
//    }

    behavior of "AuthDeps"

    it should "extract credentials from config" in {
        val config = ConfigFactory.parseString( """dart.auth.basic.credentials: "user-1:pw-1,    , user-2:pw-2,user-3:pw-3 ,, user-4 :pw-4 , user-5: pw-5,user-6 : pw-6,"""")
        val authDeps = SecureDartController.authDeps( config )
        authDeps.basicAuthCredentials shouldBe Seq( ("user-1", "pw-1"), ("user-2", "pw-2"), ("user-3", "pw-3"), ("user-4", "pw-4"), ("user-5", "pw-5"), ("user-6", "pw-6") )

        val config2 = ConfigFactory.parseString( """dart.auth.basic.credentials: "user-1:pw-1,user-2:pw-2,user-3:pw-3,user-4:pw-4,user-5:pw-5,user-6:pw-6"""")
        val authDeps2 = SecureDartController.authDeps( config2 )
        authDeps2.basicAuthCredentials shouldBe Seq( ("user-1", "pw-1"), ("user-2", "pw-2"), ("user-3", "pw-3"), ("user-4", "pw-4"), ("user-5", "pw-5"), ("user-6", "pw-6") )
    }

}
