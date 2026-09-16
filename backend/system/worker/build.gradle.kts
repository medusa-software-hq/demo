plugins {
  alias(libs.plugins.kotlin.jvm)

  `java-library`
}

// Does the work: the workflow and activity implementations, and what starts a worker polling for
// them. Deployed nowhere yet — only the local stack runs one — which is why it has no entry of its
// own.
dependencies {
  // `api`: a worker is started from a `WorkAvailability.Enabled`.
  api(project(":backend:system"))
  implementation(project(":backend:system:storage"))

  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.kotlin.test)
}

base { archivesName = "backend-system-worker" }
