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

base { archivesName = "backend" }
