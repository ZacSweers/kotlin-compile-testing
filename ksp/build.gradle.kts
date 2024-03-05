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

tasks.test {
  // KSP2 needs more memory to run
  minHeapSize = "1024m"
  maxHeapSize = "1024m"
}

dependencies {
  compileOnly(libs.kotlin.compilerEmbeddable)
  api(projects.core)

  compileOnly(libs.ksp.aaEmbeddable)
  compileOnly(libs.ksp.commonDeps)
  compileOnly(libs.ksp.api)
  compileOnly(libs.ksp)

  testImplementation(libs.ksp.commonDeps)
  testImplementation(libs.ksp.aaEmbeddable)
  testImplementation(libs.ksp)
  testImplementation(libs.autoService) {
    because("To test accessing inherited classpath symbols")
  }
  testImplementation(libs.kotlin.compilerEmbeddable)
  testImplementation(libs.ksp.api)
  testImplementation(libs.kotlin.junit)
  testImplementation(libs.mockito)
  testImplementation(libs.mockitoKotlin)
  testImplementation(libs.assertJ)
}
