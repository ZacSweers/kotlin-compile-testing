/* Adds support for KSP (https://goo.gle/ksp). */
package com.tschuchort.compiletesting

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import java.io.File
import java.io.PrintStream
import java.util.EnumSet
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
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
          projectBaseDir = compilation.kspWorkingDir

          incremental = this@Ksp2PrecursorTool.incremental
          incrementalLog = this@Ksp2PrecursorTool.incrementalLog
          allWarningsAsErrors = this@Ksp2PrecursorTool.allWarningsAsErrors
          processorOptions = this@Ksp2PrecursorTool.processorOptions.toMap()

          jvmTarget = compilation.jvmTarget
          jdkHome = compilation.jdkHome
          languageVersion = compilation.languageVersion ?: "2.0"
          apiVersion = compilation.apiVersion ?: "2.0"

          // TODO wat
          moduleName = compilation.moduleName ?: "main"
          sourceRoots = sources.filter { it.extension == "kt" }
          javaSourceRoots = sources.filter { it.extension == "java" }
          @Suppress("invisible_member", "invisible_reference")
          libraries = compilation.classpaths + compilation.commonClasspaths()

          cachesDir =
            compilation.kspCachesDir.also {
              it.deleteRecursively()
              it.mkdirs()
            }
          outputBaseDir =
            compilation.kspSourcesDir.also {
              it.deleteRecursively()
              it.mkdirs()
            }
          classOutputDir =
            compilation.kspClassesDir.also {
              it.deleteRecursively()
              it.mkdirs()
            }
          javaOutputDir =
            compilation.kspJavaSourceDir.also {
              it.deleteRecursively()
              it.mkdirs()
              compilation.registerGeneratedSourcesDir(it)
            }
          kotlinOutputDir =
            compilation.kspKotlinSourceDir.also {
              it.deleteRecursively()
              it.mkdirs()
              compilation.registerGeneratedSourcesDir(it)
            }
          resourceOutputDir =
            compilation.kspResources.also {
              it.deleteRecursively()
              it.mkdirs()
            }

          onBuilder?.invoke(this)
        }
        .build()

    val messageCollector =
      PrintingMessageCollector(output, MessageRenderer.GRADLE_STYLE, compilation.verbose)
        .filterBy(loggingLevels)
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
    }
  }
}

/** Enables KSP2. */
@OptIn(ExperimentalCompilerApi::class)
fun KotlinCompilation.useKsp2() {
  precursorTools.getOrPut("ksp2", ::Ksp2PrecursorTool)
}
