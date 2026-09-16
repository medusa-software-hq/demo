plugins {
  alias(libs.plugins.kotlin.jvm)

  `java-library`
}

// What the Service and the worker agree on about work: the workflow and its activities as
// interfaces, how Temporal is reached, and whether an environment runs work at all. Neither side's
// implementation is here — the Service only ever starts work, and the worker only ever does it.
//
// The tests are the system's own. They start a stack and call it over HTTP, which is the system
// doing its job rather than any one part doing its own, so they sit above all of them.
dependencies {
  // `api`: the workflow interfaces are written in Temporal's annotations, and a connection hands
  // out Temporal's own client.
  api(libs.temporal.sdk)

  testImplementation(project(":backend:local-stack"))
  testImplementation(testFixtures(project(":backend:local-stack")))
  testImplementation(project(":api:client"))
  // To give the client one that names who is calling.
  testImplementation(libs.okhttp)
  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlin.test)
}

// The route tests start stacks, and a stack starts its worker against a Temporal dev server, so
// they need the CLI `:backend:local-stack` downloads — unpacked where that project puts it.
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

base { archivesName = "backend-system" }
