/* Adds support for KSP (https://goo.gle/ksp). */
package com.tschuchort.compiletesting

import com.google.devtools.ksp.impl.KSPCoreEnvironment
import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import java.io.File
import java.io.PrintStream
import java.util.EnumSet
import ksp.com.intellij.openapi.application.ApplicationManager as ShadedKspApplicationManager
import ksp.com.intellij.openapi.util.Disposer as ShadedKspDisposer
import ksp.com.intellij.openapi.util.Disposer.dispose
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@ExperimentalCompilerApi
class Ksp2PrecursorTool : PrecursorTool, KspTool {
  override var withCompilation: Boolean
    get() = false
    set(value) {
      // Irrelevant/unavailable on KSP 2
    }

  override val symbolProcessorProviders: MutableList<SymbolProcessorProvider> = mutableListOf()
  override val processorOptions: MutableMap<String, String> = mutableMapOf()
  override var incremental: Boolean = false
  override var incrementalLog: Boolean = false
  override var allWarningsAsErrors: Boolean = false
  override var loggingLevels: Set<CompilerMessageSeverity> =
    EnumSet.allOf(CompilerMessageSeverity::class.java)

  // Extra hook for direct configuration of KspJvmConfig.Builder, for advanced use cases
  var onBuilder: (KSPJvmConfig.Builder.() -> Unit)? = null

  override fun execute(
    compilation: KotlinCompilation,
    output: PrintStream,
    sources: List<File>,
  ): KotlinCompilation.ExitCode {
    if (symbolProcessorProviders.isEmpty()) {
      return KotlinCompilation.ExitCode.OK
    }

    val config =
      KSPJvmConfig.Builder()
        .apply {
          projectBaseDir = compilation.kspWorkingDir.absoluteFile

          incremental = this@Ksp2PrecursorTool.incremental
          incrementalLog = this@Ksp2PrecursorTool.incrementalLog
          allWarningsAsErrors = this@Ksp2PrecursorTool.allWarningsAsErrors
          processorOptions = this@Ksp2PrecursorTool.processorOptions.toMap()

          jvmTarget = compilation.jvmTarget
          jdkHome = compilation.jdkHome
          languageVersion = compilation.languageVersion ?: KotlinVersion.CURRENT.languageVersion()
          apiVersion = compilation.apiVersion ?: KotlinVersion.CURRENT.languageVersion()

          // TODO adopt new roots model
          moduleName = compilation.moduleName ?: "main"
          sourceRoots =
            sources
              .filter { it.extension == "kt" }
              .mapNotNull { it.parentFile.absoluteFile }
              .distinct()
          javaSourceRoots =
            sources
              .filter { it.extension == "java" }
              .mapNotNull { it.parentFile.absoluteFile }
              .distinct()
          libraries = compilation.classpaths + compilation.commonClasspaths()

          cachesDir =
            compilation.kspCachesDir
              .also {
                it.deleteRecursively()
                it.mkdirs()
              }
              .absoluteFile
          outputBaseDir =
            compilation.kspSourcesDir
              .also {
                it.deleteRecursively()
                it.mkdirs()
              }
              .absoluteFile
          classOutputDir =
            compilation.kspClassesDir
              .also {
                it.deleteRecursively()
                it.mkdirs()
              }
              .absoluteFile
          javaOutputDir =
            compilation.kspJavaSourceDir
              .also {
                it.deleteRecursively()
                it.mkdirs()
                compilation.registerGeneratedSourcesDir(it)
              }
              .absoluteFile
          kotlinOutputDir =
            compilation.kspKotlinSourceDir
              .also {
                it.deleteRecursively()
                it.mkdirs()
                compilation.registerGeneratedSourcesDir(it)
              }
              .absoluteFile
          resourceOutputDir =
            compilation.kspResources
              .also {
                it.deleteRecursively()
                it.mkdirs()
              }
              .absoluteFile

          onBuilder?.invoke(this)
        }
        .build()

    val messageCollector = compilation.createMessageCollectorAccess("ksp")
    val logger =
      TestKSPLogger(
        messageCollector = messageCollector,
        allWarningsAsErrors = config.allWarningsAsErrors,
      )

    return try {
      when (KotlinSymbolProcessing(config, symbolProcessorProviders.toList(), logger).execute()) {
        KotlinSymbolProcessing.ExitCode.OK -> KotlinCompilation.ExitCode.OK
        KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR ->
          KotlinCompilation.ExitCode.COMPILATION_ERROR
      }
    } finally {
      logger.reportAll()
      clearKspLeaks()
    }
  }

  /**
   * KSP leaks its core environment because its CLI appears to be intended for single-shot use and
   * stores stuff in ThreadLocals.
   *
   * The Gradle plugin doesn't seem to run into this because it runs all the tasks in an isolated
   * classloader that dies between task runs.
   */
  private fun clearKspLeaks() {
    KSPCoreEnvironment.instance_prop.remove()
    // Doesn't _seem_ necessary but just in case, since it does appear to spin this up
    ShadedKspApplicationManager.getApplication()?.let(ShadedKspDisposer::dispose)
  }
}

private fun KotlinVersion.languageVersion(): String {
  return "$major.$minor"
}

/** Enables KSP2. */
@OptIn(ExperimentalCompilerApi::class)
fun KotlinCompilation.useKsp2() {
  precursorTools.getOrPut("ksp2", ::Ksp2PrecursorTool)
}
