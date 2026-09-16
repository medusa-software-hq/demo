plugins {
  alias(libs.plugins.kotlin.jvm)

  application
}

// How the Service is started on Cloud Run: the port comes from the environment, because Cloud Run
// chooses it and routes to it.
dependencies {
  implementation(project(":backend:system:service"))

  runtimeOnly(libs.logback.classic)
}

application { mainClass = "software.medusa.demo.backend.service.cloud_run.MainKt" }

base { archivesName = "backend-system-service-cloud-run" }
