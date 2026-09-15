plugins {
  alias(libs.plugins.kotlin.jvm)

  `java-library`
}

dependencies {
  testImplementation(project(":backend:local-stack"))
  testImplementation(testFixtures(project(":backend:local-stack")))
  testImplementation(project(":api:client"))
  // To give the client one that names who is calling.
  testImplementation(libs.okhttp)
  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlin.test)
}

// The route tests start stacks, and a stack starts its worker against a Temporal dev server, so
// they
// need the CLI `:backend:local-stack` downloads — unpacked where that project puts it.
val temporalCli =
    rootProject.layout.buildDirectory.file(
        "temporal-cli/${libs.versions.temporal.cli.get()}/temporal",
    )

tasks.withType<Test>().configureEach {
  dependsOn(":backend:local-stack:installTemporalCli")
  jvmArgumentProviders.add(
      CommandLineArgumentProvider { listOf("-Ddemo.temporal.cli=${temporalCli.get().asFile}") },
  )
}

base { archivesName = "backend" }
