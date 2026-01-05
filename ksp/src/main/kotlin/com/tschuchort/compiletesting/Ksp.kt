/** Adds support for KSP (https://goo.gle/ksp). */
package com.tschuchort.compiletesting

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import java.io.File
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/** Configure the given KSP tool for this compilation. */
@OptIn(ExperimentalCompilerApi::class)
fun KotlinCompilation.configureKsp(body: KspTool.() -> Unit) {
  useKsp2()
  getKspTool().body()
}

/** The list of symbol processors for the kotlin compilation. https://goo.gle/ksp */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.symbolProcessorProviders: MutableList<SymbolProcessorProvider>
  get() = getKspTool().symbolProcessorProviders
  set(value) {
    val tool = getKspTool()
    tool.symbolProcessorProviders.clear()
    tool.symbolProcessorProviders.addAll(value)
  }

/** The directory where generated KSP sources are written */
@OptIn(ExperimentalCompilerApi::class)
val KotlinCompilation.kspSourcesDir: File
  get() = kspWorkingDir.resolve("sources")

/** Arbitrary arguments to be passed to ksp */
@OptIn(ExperimentalCompilerApi::class)
@Deprecated(
  "Use kspProcessorOptions",
  replaceWith =
    ReplaceWith("kspProcessorOptions", "com.tschuchort.compiletesting.kspProcessorOptions"),
)
var KotlinCompilation.kspArgs: MutableMap<String, String>
  get() = kspProcessorOptions
  set(options) {
    kspProcessorOptions = options
  }

/** Arbitrary processor options to be passed to ksp */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.kspProcessorOptions: MutableMap<String, String>
  get() = getKspTool().processorOptions
  set(options) {
    val tool = getKspTool()
    tool.processorOptions.clear()
    tool.processorOptions.putAll(options)
  }

/** Controls for enabling incremental processing in KSP. */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.kspIncremental: Boolean
  get() = getKspTool().incremental
  set(value) {
    val tool = getKspTool()
    tool.incremental = value
  }

/** Controls for enabling incremental processing logs in KSP. */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.kspIncrementalLog: Boolean
  get() = getKspTool().incrementalLog
  set(value) {
    val tool = getKspTool()
    tool.incrementalLog = value
  }

/** Controls for enabling all warnings as errors in KSP. */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.kspAllWarningsAsErrors: Boolean
  get() = getKspTool().allWarningsAsErrors
  set(value) {
    val tool = getKspTool()
    tool.allWarningsAsErrors = value
  }

/**
 * Run processors and compilation in a single compiler invocation if true. See
 * [com.google.devtools.ksp.KspCliOption.WITH_COMPILATION_OPTION].
 */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.kspWithCompilation: Boolean
  get() = getKspTool().withCompilation
  set(value) {
    val tool = getKspTool()
    tool.withCompilation = value
  }

/** Sets logging levels for KSP. Default is all. */
@OptIn(ExperimentalCompilerApi::class)
var KotlinCompilation.kspLoggingLevels: Set<CompilerMessageSeverity>
  get() = getKspTool().loggingLevels
  set(value) {
    val tool = getKspTool()
    tool.loggingLevels = value
  }

@ExperimentalCompilerApi
val JvmCompilationResult.sourcesGeneratedBySymbolProcessor: Sequence<File>
  get() = outputDirectory.parentFile.resolve("ksp/sources").walkTopDown().filter { it.isFile }

@OptIn(ExperimentalCompilerApi::class)
internal val KotlinCompilation.kspJavaSourceDir: File
  get() = kspSourcesDir.resolve("java")

@OptIn(ExperimentalCompilerApi::class)
internal val KotlinCompilation.kspKotlinSourceDir: File
  get() = kspSourcesDir.resolve("kotlin")

@OptIn(ExperimentalCompilerApi::class)
internal val KotlinCompilation.kspResources: File
  get() = kspSourcesDir.resolve("resources")

/** The working directory for KSP */
@OptIn(ExperimentalCompilerApi::class)
internal val KotlinCompilation.kspWorkingDir: File
  get() = workingDir.resolve("ksp")

/** The directory where compiled KSP classes are written */
// TODO this seems to be ignored by KSP and it is putting classes into regular classes directory
//  but we still need to provide it in the KSP options builder as it is required
//  once it works, we should make the property public.
@OptIn(ExperimentalCompilerApi::class)
internal val KotlinCompilation.kspClassesDir: File
  get() = kspWorkingDir.resolve("classes")

/** The directory where compiled KSP caches are written */
@OptIn(ExperimentalCompilerApi::class)
internal val KotlinCompilation.kspCachesDir: File
  get() = kspWorkingDir.resolve("caches")
