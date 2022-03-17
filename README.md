# Dart Auth Commons

Libraries used throughout DART for authentication and authorization  
[![build and publish](https://github.com/twosixlabs-dart/dart-auth-commons/actions/workflows/build-and-publish.yml/badge.svg)](https://github.com/twosixlabs-dart/dart-auth-commons/actions/workflows/build-and-publish.yml)

## Dependencies

In addition to the publicly available third-party libraries it uses, dart-auth has dependencies 
on a number of other Scala libraries. In order to build DART these dependencies must be 
accessible via the local filesystem (in the SBT cache) or over the network via
[Sonatype Nexus](https://www.sonatype.com/products/repository-oss-download) where they are
published. dart-auth requires the following dependencies to be built/installed:


| Group ID                 | Artifact ID          |
|--------------------------|----------------------|
| com.twosixlabs.dart      | dart-exceptions_2.12 |
| com.twosixlabs.dart      | dart-json_2.12       |
| com.twosixlabs.dart      | dart-test-base_2.12  |
| com.twosixlabs.cdr4s     | cdr4s-core_2.12      |

In addition to the above DART libraries, dart-auth depends on the open source library 
`johnhungerford/scala-rbac`, which is maintained in party by the DART team.

## Building

This project is built using SBT. For more information on installation and configuration
of SBT please [see the documentation](https://www.scala-sbt.org/1.x/docs/)

dart-auth is a library containing no runnable main classes. The only supported build tasks are
compilation, testing, and publication:

```bash
sbt clean         # clear out all build artifacts
sbt compile       
sbt test          # run all test suites
sbt publish       # publish all modules to maven
sbt publishLocal  # publish all modules locally
```

all tasks can be executed relative to a single module by prefixing the task with the
module name as defined in `build.sbt`. Note that the `core` module is cross-compiled 
to scala.js which generates the project ids `coreJS` and `coreJVM`.

```bash
sbt coreJVM/compile
sbt coreJS/test
sbt keycloakCommon/publishLocal
```

## Project structure

The build is into the following modules:

```
root
 |
 |-- core
 |
 |-- controllers
 |
 |-- keycloak-common
 |
 |-- tenant-index
 |     |
 |     |-- arrango-tenants
 |     |
 |     |-- keycloak-tenants
 |      
 |
 |-- user-store
       |
        -- keycloak-users

```

1. `core` contains the user, group, and tenants data models; authorization logic; and tenants and 
user data service interface definitions.
2. `controllers` contains the abstract scalatra servlet classes used by all DART REST services, 
which automatically handle authentication and contain utilities for enforcing authorization on 
certain operations. 
3. `keycloak-common` contains a keycloak admin client
4. `tenant-index` contains service implementations for reading and writing tenant data 
in arrango-db (now canonical), and keycloak
5. `user-store` contains service implementations for reading and writing user data. This is 
currently in keycloak only.
