plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ksp)
  alias(libs.plugins.micronaut.library)
  alias(libs.plugins.fabrikt)

  `java-library`
}

// The routes, and what a backend has to implement to answer them. Knows the wire and the shapes,
// and nothing about counters: `ApiHandler` is where the product starts.
dependencies {
  ksp("io.micronaut.serde:micronaut-serde-processor")

  // `api`: ApiHandler is written in these.
  api(project(":api"))

  // The routes suspend, and Micronaut's bridge for that needs coroutines at run time. Nothing
  // here names them, so only running the service finds this missing.
  implementation(libs.kotlinx.coroutines.core)

  implementation("io.micronaut:micronaut-http-server-netty")
  implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
  implementation("io.micronaut.serde:micronaut-serde-jackson")

  testImplementation(libs.kotlin.test)
}

micronaut {
  runtime("netty")
  testRuntime("junit5")
  processing {
    incremental(true)
    annotations("software.medusa.demo.*")
  }
}

fabrikt {
  generate("demo") {
    apiFile = rootProject.file("api/openapi/demo.yaml")
    basePackage = "software.medusa.demo.api.raw"
    validationLibrary = NoValidation

    model { micronautIntrospection = true }

    controller {
      generate = true
      groupByTag = true
      // The handler suspends, so the route that calls it can too, rather than blocking a thread on
      // it.
      suspendModifier = true
      target = Micronaut
    }
  }
}

// KSP needs an explicit dependency on the Fabrikt generation task
tasks
    .matching { it.name == "kspKotlin" }
    .configureEach { dependsOn(tasks.named("fabriktGenerate")) }

// Exclude generated sources from being linted/formatted.
run {
  tasks.withType<com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask>().configureEach {
    exclude { it.file.absolutePath.contains("/generated/") }
  }

  tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { exclude("**/generated/**") }
}

base { archivesName = "api-server" }
