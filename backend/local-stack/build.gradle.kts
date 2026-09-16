import java.net.URI
import java.security.MessageDigest

plugins {
  alias(libs.plugins.kotlin.jvm)

  application
  `java-test-fixtures`
}

dependencies {
  api(project(":backend:system:service"))
  // `api`: the stack runs a worker beside the Service, and callers choose the Temporal it runs on.
  api(project(":backend:system:worker"))

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

/**
 * The Temporal CLI, whose `server start-dev` is the stack's Temporal.
 *
 * Downloaded rather than asked to be installed, the same bargain the embedded Postgres makes: one
 * pinned version, checked against the checksums its release publishes, for whichever platform the
 * build is running on. Unpacked into the root build directory, where every project whose tests
 * start a stack can find it, and handed to those JVMs as the `demo.temporal.cli` system property.
 */
val temporalCliVersion = libs.versions.temporal.cli.get()

/** From the release's `checksums.txt`. A new version means new ones, copied from there. */
val temporalCliSha256 =
    mapOf(
        "darwin_amd64" to "0eed9a02008ba0d1c5417fc1aa706c9016166eae7216ae161ad95eccc6a775ca",
        "darwin_arm64" to "77c5bef1753ddfcdcaced2a2d44207aeced1c776e7bcbf94520c7911bd0c4080",
        "linux_amd64" to "6f0afac1e9ddea71f480c43a49f5db5167a244c21db923707f069a79bcabdfea",
        "linux_arm64" to "5972ce781d7f28644b353e4177007e7da8e48a316b8458267054b24de2308e09",
    )

val temporalCliPlatform: String = run {
  val osName = System.getProperty("os.name").lowercase()
  val os =
      when {
        osName.contains("mac") -> "darwin"
        osName.contains("linux") -> "linux"
        else -> error("No Temporal CLI is pinned for $osName")
      }
  val arch =
      when (val osArch = System.getProperty("os.arch")) {
        "aarch64",
        "arm64" -> "arm64"
        "amd64",
        "x86_64" -> "amd64"
        else -> error("No Temporal CLI is pinned for $osArch")
      }

  "${os}_$arch"
}

val temporalCliArchive =
    layout.buildDirectory.file(
        "temporal-cli/temporal_cli_${temporalCliVersion}_$temporalCliPlatform.tar.gz",
    )

val downloadTemporalCli by tasks.registering {
  description = "Downloads the Temporal CLI, and checks it against its published checksum."

  val url =
      "https://github.com/temporalio/cli/releases/download/v$temporalCliVersion/" +
          "temporal_cli_${temporalCliVersion}_$temporalCliPlatform.tar.gz"
  val expectedSha256 = temporalCliSha256.getValue(temporalCliPlatform)
  val archive = temporalCliArchive

  inputs.property("url", url)
  inputs.property("sha256", expectedSha256)
  outputs.file(archive)

  doLast {
    val file = archive.get().asFile

    file.parentFile.mkdirs()
    URI(url).toURL().openStream().use { input -> file.outputStream().use(input::copyTo) }

    val actualSha256 =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
          "%02x".format(it)
        }

    if (actualSha256 != expectedSha256) {
      file.delete()

      throw GradleException(
          "The Temporal CLI downloaded from $url does not match its checksum: " +
              "expected $expectedSha256, got $actualSha256",
      )
    }
  }
}

val installTemporalCli by
    tasks.registering(Sync::class) {
      description = "Unpacks the Temporal CLI where the stack's JVMs are told to look for it."

      dependsOn(downloadTemporalCli)
      from(tarTree(resources.gzip(temporalCliArchive))) { include("temporal") }
      into(rootProject.layout.buildDirectory.dir("temporal-cli/$temporalCliVersion"))
      filePermissions { unix("rwxr-xr-x") }
    }

val temporalCli =
    rootProject.layout.buildDirectory.file("temporal-cli/$temporalCliVersion/temporal")

tasks.withType<Test>().configureEach {
  dependsOn(installTemporalCli)
  jvmArgumentProviders.add(
      CommandLineArgumentProvider { listOf("-Ddemo.temporal.cli=${temporalCli.get().asFile}") },
  )
}

tasks.named<JavaExec>("run") {
  dependsOn(installTemporalCli)
  jvmArgumentProviders.add(
      CommandLineArgumentProvider { listOf("-Ddemo.temporal.cli=${temporalCli.get().asFile}") },
  )
}

base { archivesName = "backend-local-stack" }
