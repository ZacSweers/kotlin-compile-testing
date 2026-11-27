import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  kotlin("kapt")
  alias(libs.plugins.ksp)
  alias(libs.plugins.buildconfig)
  alias(libs.plugins.mavenPublish)
}

buildConfig {
  className.set("BuildConfig")
  packageName.set("com.tschuchort.compiletesting")
  generateAtSync = false
  useKotlinOutput { topLevelConstants = true }
  sourceSets {
    getByName("test") {
      buildConfigField("String", "KOTLIN_VERSION", "\"${libs.versions.kotlin.get()}\"")
    }
  }
}

tasks
  .named { it == "kspTestKotlin" }
  .configureEach { dependsOn(tasks.named { it == "generateTestBuildConfig" }) }

val testAnnotations = configurations.register("testAnnotations")
val testProcessor = configurations.register("testProcessor")
val mockitoAgent = configurations.resolvable("mockitoAgent") { isTransitive = false }

dependencies {
  ksp(libs.autoService.ksp)

  implementation(libs.autoService)
  implementation(libs.okio)
  implementation(libs.classgraph)

  // These dependencies are only needed as a "sample" compiler plugin to test that
  // running compiler plugins passed via the pluginClasspath CLI option works
  testRuntimeOnly(libs.kotlin.scriptingCompiler)

  api(libs.kotlin.compilerEmbeddable)
  api(libs.kotlin.annotationProcessingEmbeddable)

  testImplementation(projects.testProcessor)
  testImplementation(libs.kotlin.junit)
  testImplementation(libs.mockito)
  testImplementation(libs.mockitoKotlin)
  testImplementation(libs.assertJ)

  testAnnotations(projects.testAnnotations)
  testProcessor(projects.testProcessor)
  add(mockitoAgent.name, libs.mockito)
}

tasks.test {
  maxParallelForks = Runtime.getRuntime().availableProcessors() * 2

  jvmArgumentProviders.add(
    objects.newInstance<MockitoAgentArguments>().apply {
      agentJar.from(mockitoAgent)
    }
  )
  // Mockito appends to the bootstrap classpath, which prevents application class-data sharing.
  jvmArgs("-Xshare:off")

  systemProperty("testAnnotationsClasspath", testAnnotations.get().asPath)
  systemProperty("testProcessorClasspath", testProcessor.get().asPath)
}

tasks.withType<KotlinCompile>().configureEach {
  val isTest = name.contains("test", ignoreCase = true)
  compilerOptions {
    if (isTest) {
      optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
  }
}

tasks.withType<Jar>().configureEach { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }

abstract class MockitoAgentArguments : CommandLineArgumentProvider {
  @get:Classpath abstract val agentJar: ConfigurableFileCollection

  override fun asArguments(): Iterable<String> = listOf("-javaagent:${agentJar.singleFile}")
}
