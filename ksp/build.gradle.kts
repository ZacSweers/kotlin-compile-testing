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

dependencies {
  api(projects.core)
  api(libs.ksp.api)

  implementation(libs.ksp)
  implementation(libs.ksp.commonDeps)
  implementation(libs.ksp.aaEmbeddable)

  testImplementation(libs.kotlinpoet.ksp)
  testImplementation(libs.autoService) {
    because("To test accessing inherited classpath symbols")
  }
  testImplementation(libs.kotlin.junit)
  testImplementation(libs.mockito)
  testImplementation(libs.mockitoKotlin)
  testImplementation(libs.assertJ)
}
