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

// It's not possible to test both KSP 1 and KSP 2 in the same compilation unit
val testKsp2 = providers.systemProperty("kct.test.useKsp2").getOrElse("false").toBoolean()

tasks.test {
  if (testKsp2) {
    systemProperty("kct.test.useKsp2", testKsp2)
  }
}

dependencies {
  compileOnly(libs.kotlin.compilerEmbeddable)
  api(projects.core)

  // KSP's AA dependencies point to the wrong common-deps artifact
  compileOnly(libs.ksp.aa) { exclude(group = "com.google.devtools.ksp", module = "common-deps") }
  compileOnly(libs.ksp.commonDeps)
  compileOnly(libs.ksp.api)
  compileOnly(libs.ksp)

  if (testKsp2) {
    testImplementation(libs.ksp.aa.embeddable) {
      exclude(group = "com.google.devtools.ksp", module = "common-deps")
    }
    testImplementation(libs.ksp.commonDeps)
    testImplementation(libs.ksp.cli)
  } else {
    testImplementation(libs.ksp)
  }
  testImplementation(libs.kotlin.compilerEmbeddable)
  testImplementation(libs.ksp.api)
  testImplementation(libs.kotlin.junit)
  testImplementation(libs.mockito)
  testImplementation(libs.mockitoKotlin)
  testImplementation(libs.assertJ)
}
