plugins {
  alias(libs.plugins.kotlin.jvm)

  application
}

dependencies {
  api(project(":backend:service"))
  // Migrates to a chosen version when writing the migration fixture; the Service does not expose
  // it.
  implementation(project(":backend:storage"))

  // The stack starts its own Postgres, so running it locally and testing against it both need
  // Docker.
  implementation(libs.testcontainers.postgresql)

  // Migrates to a chosen version when writing the migration fixture.
  implementation(libs.flyway.core)

  constraints {
    implementation(libs.commons.compress) {
      because(
          "Micronaut's platform pins 1.26.0, whose tar writer needs commons-codec while its POM " +
              "declares it optional, so copying a file into a container fails with " +
              "NoClassDefFoundError: org/apache/commons/codec/Charsets. Later releases declare it."
      )
    }
  }

  runtimeOnly(libs.logback.classic)

  testImplementation(libs.kotlin.test)
}

application { mainClass = "software.medusa.demo.backend.stack.MainKt" }

/** Where the migration test's starting database is kept, and the rows it is built with. */
val fixtureDirectory =
    rootProject.layout.projectDirectory.dir("backend/src/test/resources/database")

tasks.register<JavaExec>("writeMigrationFixture") {
  group = "database"
  description = "Writes the dump the migration test starts from, at -PtargetVersion. Needs Docker."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass = "software.medusa.demo.backend.stack.WriteMigrationFixtureKt"

  val targetVersion = providers.gradleProperty("targetVersion")

  // Read when the task runs, so every other build does not need the property.
  argumentProviders.add(
      CommandLineArgumentProvider {
        listOf(
            targetVersion.orNull
                ?: error("Pass the version to migrate to: -PtargetVersion=<version>"),
            fixtureDirectory.file("oldest-supported.seed.sql").asFile.path,
            fixtureDirectory.file("oldest-supported.sql").asFile.path,
        )
      },
  )
}

base { archivesName = "backend-local-stack" }
