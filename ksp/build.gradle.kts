import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  alias(libs.plugins.mavenPublish)
}

tasks
  .withType<KotlinCompile>()
  .matching { it.name.contains("test", ignoreCase = true) }
  .configureEach {
    compilerOptions { optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi") }
  }

tasks.test { maxParallelForks = Runtime.getRuntime().availableProcessors() * 2 }

// From https://www.liutikas.net/2025/01/12/Kotlin-Library-Friends.html
// Create configurations we can use to track friend libraries
configurations {
  val friendsApi =
    register("friendsApi") {
      isCanBeResolved = true
      isCanBeConsumed = false
      isTransitive = true
    }
  val friendsImplementation =
    register("friendsImplementation") {
      isCanBeResolved = true
      isCanBeConsumed = false
      isTransitive = false
    }
  val friendsTestImplementation =
    register("friendsTestImplementation") {
      isCanBeResolved = true
      isCanBeConsumed = false
      isTransitive = false
    }
  configurations.configureEach {
    if (name == "implementation") {
      extendsFrom(friendsApi.get(), friendsImplementation.get())
    }
    if (name == "api") {
      extendsFrom(friendsApi.get())
    }
    if (name == "testImplementation") {
      extendsFrom(friendsTestImplementation.get())
    }
  }
}

// Make these libraries friends :)
tasks.withType<KotlinCompile>().configureEach {
  configurations.findByName("friendsApi")?.let {
    friendPaths.from(it.incoming.artifactView {}.files)
  }
  configurations.findByName("friendsImplementation")?.let {
    friendPaths.from(it.incoming.artifactView {}.files)
  }
  configurations.findByName("friendsTestImplementation")?.let {
    friendPaths.from(it.incoming.artifactView {}.files)
  }
}

dependencies {
  "friendsApi"(projects.core)
  api(libs.ksp.api)

  implementation(libs.ksp)
  implementation(libs.ksp.commonDeps)
  implementation(libs.ksp.aaEmbeddable)

  testImplementation(libs.kotlinpoet.ksp)
  testImplementation(libs.autoService) { because("To test accessing inherited classpath symbols") }
  testImplementation(libs.kotlin.junit)
  testImplementation(libs.mockito)
  testImplementation(libs.mockitoKotlin)
  testImplementation(libs.assertJ)
}
