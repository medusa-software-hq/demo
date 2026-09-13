plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ksp)
  alias(libs.plugins.micronaut.library)

  `java-library`
}

// The Service: the store, the handler that answers the API out of it, and the starter that brings
// the whole thing up. The routes themselves belong to `:api:server`.
dependencies {
  implementation(project(":backend"))
  // `api`: the store and the handler are written in these.
  api(project(":core"))
  api(project(":api:server"))

  implementation("io.micronaut:micronaut-http-server-netty")
  implementation(libs.kotlinx.coroutines.core)

  // `api`: `Database.connect` hands its caller the pool, which the caller then owns and closes.
  api(libs.hikaricp)
  implementation(libs.flyway.core)
  runtimeOnly(libs.flyway.database.postgresql)
  runtimeOnly(libs.postgresql)
  implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")

  testImplementation(libs.kotlin.test)
}

micronaut {
  runtime("netty")
  testRuntime("junit5")
  processing {
    incremental(true)
    annotations("software.medusa.demo.*")
  }
}

base { archivesName = "backend-service" }
