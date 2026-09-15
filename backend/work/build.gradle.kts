plugins {
  alias(libs.plugins.kotlin.jvm)

  `java-library`
}

// What the Service and the worker agree on about work: the workflow and its activities as
// interfaces, how Temporal is reached, and whether an environment runs work at all. Neither side's
// implementation is here — the Service only ever starts work, and the worker only ever does it.
dependencies {
  // `api`: the workflow interfaces are written in Temporal's annotations, and a connection hands
  // out Temporal's own client.
  api(libs.temporal.sdk)

  testImplementation(libs.kotlin.test)
}

base { archivesName = "backend-work" }
