package com.twosixlabs.dart.auth.controllers

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonInclude, JsonProperty}
import com.twosixlabs.dart.auth.groups.ProgramManager
import com.twosixlabs.dart.auth.user.DartUser
import com.twosixlabs.dart.exceptions.AuthenticationException
import com.twosixlabs.dart.exceptions.ExceptionImplicits.TryExceptionLogging
import com.typesafe.config.Config
import org.hungerford.rbac.SecureScalatraServlet
import org.hungerford.rbac.http.exceptions.{FailedAuthenticationException, MissingAuthenticationHeaderException}
import org.slf4j.{Logger, LoggerFactory}

import javax.servlet.http.HttpServletRequest
import scala.beans.BeanProperty
import scala.util.{Failure, Success, Try}

object SecureDartController {
    trait Dependencies extends AuthDependencies {
        // Configuration fields
        val serviceName : String
    }

    trait AuthDependencies {
        val secretKey : Option[ String ]
        val useDartAuth : Boolean
        val basicAuthCredentials : Seq[ (String, String) ]

        lazy val useBasicAuth : Boolean = basicAuthCredentials.nonEmpty
        lazy val bypassAuth : Boolean = !useDartAuth && !useBasicAuth
    }

    def deps( serviceNameIn : String, secretKeyIn : Option[ String ], useDartAuthIn : Boolean, basicAuthCredsIn : Seq[ (String, String) ] ) : Dependencies =
        new Dependencies {
            override val serviceName: String = serviceNameIn
            override val secretKey: Option[ String ] = secretKeyIn
            override val useDartAuth : Boolean = useDartAuthIn
            override val basicAuthCredentials : Seq[ (String, String) ] = basicAuthCredsIn
        }

    def deps( serviceNameIn : String, authDependencies: AuthDependencies ) : Dependencies = deps(
        serviceNameIn,
        authDependencies.secretKey,
        authDependencies.useDartAuth,
        authDependencies.basicAuthCredentials,
    )

    def deps( serviceNameIn : String, config : Config ) : Dependencies = deps(
        serviceNameIn,
        authDeps( config ),
    )

    def authDeps( secretKeyIn : Option[ String ] = None, useDartAuthIn : Boolean = false, basicAuthCredsIn : Seq[ (String, String) ] = Seq.empty[ (String, String) ] ) : AuthDependencies =
        new AuthDependencies {
            override val secretKey: Option[ String ] = secretKeyIn
            override val useDartAuth: Boolean = useDartAuthIn
            override val basicAuthCredentials: Seq[ (String, String) ] = basicAuthCredsIn
        }

    def authDeps( config : Config ) : AuthDependencies = authDeps(
        Try( config.getString( "dart.auth.secret" ) ).toOption,
        Try( ! config.getBoolean( "dart.auth.bypass") ).getOrElse( true ),
        Try( config.getString( "dart.auth.basic.credentials" ) )
          .logged
          .orElse( Try( System.getenv( "DART_AUTH_BASIC_CREDENTIALS" ) ) )
          .map( _.split( "," )
                  .map( v => v.trim )
                  .filter( _.nonEmpty )
                  .map( _.split( ":" ) )
                  .map( v => (v( 0 ).trim, v( 1 ).trim) )
                  .toSeq ).getOrElse( Seq.empty[ (String, String) ] ),
    )

}

trait SecureDartController
  extends SecureScalatraServlet[ DartUser ]
    with SecureDartController.Dependencies
    with DartAuthHttpExtractor {

    override val authHeaderKey : String = "Authorization"

    val LOG : Logger = LoggerFactory.getLogger( getClass )

    override def authenticateUser( authHeader : String  ) : DartUser = {
        val trimmedHeader = authHeader.trim
        if ( useBasicAuth && trimmedHeader.toLowerCase.startsWith( "basic " ) ) {
            val basicAuthPayload = trimmedHeader.drop( 6 )
            import java.nio.charset.StandardCharsets
            import java.util.Base64
            val creds = Try {
                val credDecoded = Base64.getDecoder.decode( basicAuthPayload )
                val credentials = new String( credDecoded, StandardCharsets.UTF_8 )
                val credsArray = credentials.split( ":", 2 )
                (credsArray( 0 ), credsArray( 1 ))
            }.getOrElse( throw new AuthenticationException( "Unable to parse basic auth header" ) )

            if ( basicAuthCredentials.contains( creds ) ) DartUser( "basic-auth", Set( ProgramManager ) )
            else throw new AuthenticationException( s"Invalid basic auth credentials: username: ${creds._1}, password: ${creds._2}" )
        } else userFromAuthHeader( authHeaderKey, authHeader, secretKey, serviceName )
    }

    override def authenticateUser( req: HttpServletRequest ) : DartUser = {
        if ( bypassAuth ) DartUser( "bypass-auth", Set( ProgramManager ) )
        else Try( authenticateUser(
            request.header( authHeaderKey )
              .getOrElse( throw new MissingAuthenticationHeaderException( authHeaderKey ) )
            )
        ) match {
            case Success( res ) => res
            case Failure( e : MissingAuthenticationHeaderException ) => throw new AuthenticationException( e.getMessage )
            case Failure( e : FailedAuthenticationException ) => throw new AuthenticationException( e.getMessage )
            case Failure( e ) => throw e
        }
    }
}

@JsonInclude( Include.NON_EMPTY )
@JsonIgnoreProperties( ignoreUnknown = true )
case class JWT(
    @BeanProperty @JsonProperty( value = "exp" ) exp : String,
    @BeanProperty @JsonProperty( value = "preferred_username" ) userName : Option[ String ],
    @BeanProperty @JsonProperty( value = "client_id" ) clientId : Option[ String ],
    @BeanProperty @JsonProperty( value = "group" ) groups : Option[ List[ String ] ],
    @BeanProperty @JsonProperty( value = "aud" ) aud : Option[ List[ String ] ] = None,
)
