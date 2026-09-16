plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ksp)
  alias(libs.plugins.micronaut.library)

  `java-library`
}

// The Service: the handler that answers the API out of the storage layer, and the starter that
// brings the whole thing up. The routes themselves belong to `:api:server`, and the data to
// `:backend:system:storage`.
dependencies {
  implementation(project(":backend:system:storage"))
  // `api`: the Service is started with a `WorkAvailability`.
  api(project(":backend:system"))
  // `api`: the handler is written in these.
  api(project(":core"))
  api(project(":api:server"))

  implementation("io.micronaut:micronaut-http-server-netty")
  implementation(libs.kotlinx.coroutines.core)
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

base { archivesName = "backend-system-service" }
