/** Adds support for KSP (https://goo.gle/ksp). */
package com.tschuchort.compiletesting

import com.facebook.buck.jvm.java.javax.com.tschuchort.compiletesting.PrecursorTool
import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.processing.impl.MessageCollectorBasedKSPLogger
import java.io.File
import java.io.PrintStream
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@ExperimentalCompilerApi
internal class Ksp2PrecursorTool : PrecursorTool, KspTool {
  private val configBuilder: KSPJvmConfig.Builder =
    KSPJvmConfig.Builder().apply {
      incremental = false
      incrementalLog = false
      allWarningsAsErrors = false
    }

  override var withCompilation: Boolean
    get() = false
    set(value) {
      // Unsupported on KSP 2
    }

  override var symbolProcessorProviders: List<SymbolProcessorProvider> = mutableListOf()

  override var processorOptions: Map<String, String>
    get() = configBuilder.processorOptions
    set(value) {
      configBuilder.processorOptions = value
    }

  override var incremental: Boolean
    get() = configBuilder.incremental
    set(value) {
      configBuilder.incremental = value
    }

  override var incrementalLog: Boolean
    get() = configBuilder.incrementalLog
    set(value) {
      configBuilder.incrementalLog = value
    }

  override var allWarningsAsErrors: Boolean
    get() = configBuilder.allWarningsAsErrors
    set(value) {
      configBuilder.allWarningsAsErrors = value
    }

  override fun execute(
    compilation: KotlinCompilation,
    output: PrintStream,
    sources: List<File>,
  ): KotlinCompilation.ExitCode {
    if (symbolProcessorProviders.isEmpty()) {
      return KotlinCompilation.ExitCode.OK
    }

    val config =
      configBuilder
        .apply {
          projectBaseDir = compilation.kspWorkingDir

          jvmTarget = compilation.jvmTarget
          jdkHome = compilation.jdkHome
          languageVersion = compilation.languageVersion ?: "2.0"
          apiVersion = compilation.apiVersion ?: "2.0"

          // TODO wat
          moduleName = compilation.moduleName ?: "main"
          sourceRoots = sources.filter { it.extension == "kt" }
          javaSourceRoots = sources.filter { it.extension == "java" }
          libraries = compilation.classpaths

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
        }
        .build()
    val messageCollector =
      PrintingMessageCollector(output, MessageRenderer.GRADLE_STYLE, compilation.verbose)
    val messageCollectorBasedKSPLogger =
      MessageCollectorBasedKSPLogger(
        messageCollector = messageCollector,
        wrappedMessageCollector = messageCollector,
        allWarningsAsErrors = config.allWarningsAsErrors,
      )

    return try {
      when (
        KotlinSymbolProcessing(config, symbolProcessorProviders, messageCollectorBasedKSPLogger)
          .execute()
      ) {
        KotlinSymbolProcessing.ExitCode.OK -> KotlinCompilation.ExitCode.OK
        KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR ->
          KotlinCompilation.ExitCode.COMPILATION_ERROR
      }
    } finally {
      messageCollectorBasedKSPLogger.reportAll()
    }
  }
}

/** Enables KSP2. */
@OptIn(ExperimentalCompilerApi::class)
fun KotlinCompilation.useKsp2() {
  precursorTools.getOrPut("ksp2", ::Ksp2PrecursorTool)
}
