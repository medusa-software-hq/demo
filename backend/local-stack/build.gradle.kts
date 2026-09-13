plugins {
  alias(libs.plugins.kotlin.jvm)

  application
  `java-test-fixtures`
}

dependencies {
  api(project(":backend:service"))

  // Real Postgres binaries with no Docker daemon in the way: the stack brings its own database, so
  // running it locally or testing against it needs nothing installed.
  implementation(libs.zonky.embedded.postgres)
  // embedded-postgres ships amd64 bundles only; an Apple Silicon Mac and an arm64 runner need their
  // own, or they run amd64 under emulation where there is any.
  runtimeOnly(platform(libs.zonky.embedded.postgres.binaries.bom))
  runtimeOnly(libs.zonky.embedded.postgres.binaries.darwin.arm64v8)
  runtimeOnly(libs.zonky.embedded.postgres.binaries.linux.arm64v8)

  runtimeOnly(libs.logback.classic)

  testImplementation(libs.kotlin.test)
}

application { mainClass = "software.medusa.demo.backend.stack.MainKt" }

base { archivesName = "backend-local-stack" }
