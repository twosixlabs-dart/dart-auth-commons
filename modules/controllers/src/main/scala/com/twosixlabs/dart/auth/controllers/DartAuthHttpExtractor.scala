package com.twosixlabs.dart.auth.controllers

import com.twosixlabs.dart.auth.groups.{DartGroup, ProgramManager, TenantGroup}
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, GlobalCorpus, Leader, Member, ReadOnly}
import com.twosixlabs.dart.auth.user.DartUser
import com.twosixlabs.dart.exceptions.AuthenticationException
import com.twosixlabs.dart.exceptions.ExceptionImplicits.TryExceptionLogging
import com.twosixlabs.dart.json.JsonFormat.unmarshalTo
import pdi.jwt.{Jwt, JwtAlgorithm}
import pdi.jwt.exceptions.{JwtException, JwtExpirationException, JwtNotBeforeException, JwtValidationException}

import scala.util.{Failure, Success}
import scala.util.matching.Regex

trait DartAuthHttpExtractor {

    private val HeaderPattern : Regex = """Bearer (.+)$""".r

    def userFromAuthHeader( headerKey : String, headerValue : String, secretKey : Option[ String ], serviceName : String ) : DartUser = {
        val tokenString : String = headerValue.trim match {
            case HeaderPattern( tokenStr ) => tokenStr
            case _ => throw new AuthenticationException( s"unable to parse $headerKey header" )
        }
        val key = secretKey.getOrElse( "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAhZMk13jwrKlgmOjlr1EJhdvx88iIswxOkPhbNcfKJLhRWNx9RhYgp7e5oyg0FSvDfn0y6Xb3pPUYhEp3ujgIFDSQA8wdNRif4znuKW9JZpa0RfDKa3WLxqerlgqCWl6bP4vftiNi+G8N4OV4bkrl9njOhlRreIBjm/xuAGiqWYXxA7If7nwn3ATcE1MFniGtp21YawJmmJPWFUM8E8K9Il1F784ZLuZqoAQXUXSTtqKCVBnwAL24mKxNVnup3MhKnxipuBkBpnAbSVJQqwBtaL3AJc+uB484fuuuuip4FjCmIobDr+c3kADepi5qF6qK/d2bRSzNKOzIa4TV0dz4FwIDAQAB" )
        Jwt.decodeAll( tokenString, key, Seq( JwtAlgorithm.RS256 ) ).loggedInfo match {
            case Failure( _ : JwtExpirationException ) => throw new AuthenticationException( "expired token" )
            case Failure( _ : JwtNotBeforeException ) => throw new AuthenticationException( "token not yet valid" )
            case Failure( _ : JwtValidationException ) => throw new AuthenticationException( "token failed validation" )
            case Failure( _ : JwtException ) => throw new AuthenticationException( "unable to authenticate token" )
            case Success( (_, jwtClaim, _) ) => {
                val jwt = unmarshalTo( jwtClaim.content, classOf[ JWT ] ).get
                if ( !jwt.aud.toList.flatMap( _.toList ).contains( serviceName ) ) throw new AuthenticationException( "token is not valid for this audience" )
                val ProgramManagerPattern : Regex = """/program-manager""".r
                val GroupPattern : Regex = """/([a-z\-]+)\/([a-z\-]+)""".r
                val userName = jwt.userName.getOrElse( jwt.clientId.getOrElse( throw new AuthenticationException( "token contains neither username nor client id" ) ) )
                val groups : Set[ DartGroup ] = jwt.groups match {
                    case Some( grps : List[ String ] ) => grps.map( _.trim.toLowerCase ) flatMap {
                        case ProgramManagerPattern() => List( ProgramManager )
                        case GroupPattern( tenantStr, roleStr ) =>
                            val tenant = tenantStr match {
                                case "global" => GlobalCorpus
                                case _ => CorpusTenant( tenantStr, GlobalCorpus )
                            }
                            roleStr match {
                                case "leader" => List( TenantGroup( tenant, Leader ) )
                                case "member" => List( TenantGroup( tenant, Member ) )
                                case "read-only" => List( TenantGroup( tenant, ReadOnly ) )
                                case _ => List()
                            }
                        case _ => List()
                    } toSet
                    case None => Set.empty
                }

                DartUser( userName, groups)
            }
        }
    }

}
