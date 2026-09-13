plugins {
  alias(libs.plugins.kotlin.jvm)

  application
}

dependencies {
  api(project(":backend:service"))

  // The stack starts its own Postgres, so running it locally and testing against it both need
  // Docker.
  implementation(libs.testcontainers.postgresql)

  runtimeOnly(libs.logback.classic)

  testImplementation(libs.kotlin.test)
}

application { mainClass = "software.medusa.demo.backend.stack.MainKt" }

base { archivesName = "backend-local-stack" }
