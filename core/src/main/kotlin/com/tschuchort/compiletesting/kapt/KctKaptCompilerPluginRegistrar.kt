package com.facebook.buck.jvm.java.javax.com.tschuchort.compiletesting.kapt

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.config.JavaSourceRoot
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.fir.extensions.FirAnalysisHandlerExtension
import org.jetbrains.kotlin.kapt.FirKaptAnalysisHandlerExtension
import org.jetbrains.kotlin.kapt.KAPT_OPTIONS
import org.jetbrains.kotlin.kapt.base.AptMode
import org.jetbrains.kotlin.kapt.base.Kapt
import org.jetbrains.kotlin.kapt.base.KaptFlag
import org.jetbrains.kotlin.kapt.base.KaptOptions
import org.jetbrains.kotlin.kapt.base.LoadedProcessors
import org.jetbrains.kotlin.kapt.base.incremental.IncrementalProcessor
import org.jetbrains.kotlin.kapt.base.logString
import org.jetbrains.kotlin.kapt.base.util.KaptLogger
import org.jetbrains.kotlin.kapt.util.MessageCollectorBackedKaptLogger

@ExperimentalCompilerApi
internal class KctKaptCompilerPluginRegistrar(
  private val processors: List<IncrementalProcessor>,
  private val kaptOptions: KaptOptions.Builder
) : CompilerPluginRegistrar() {
    override val supportsK2 = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        if (!configuration.getBoolean(CommonConfigurationKeys.USE_FIR)) return

        if (processors.isEmpty())
            return

        val contentRoots = configuration[CLIConfigurationKeys.CONTENT_ROOTS] ?: emptyList()

        val optionsBuilder = kaptOptions.apply {
//            projectBaseDir = project.basePath?.let(::File)
            compileClasspath.addAll(contentRoots.filterIsInstance<JvmClasspathRoot>().map { it.file })
            javaSourceRoots.addAll(contentRoots.filterIsInstance<JavaSourceRoot>().map { it.file })
            classesOutputDir = classesOutputDir ?: configuration.get(JVMConfigurationKeys.OUTPUT_DIRECTORY)
        }

        val messageCollector = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY)
            ?: PrintingMessageCollector(
              System.err,
              MessageRenderer.PLAIN_FULL_PATHS,
              optionsBuilder.flags.contains(KaptFlag.VERBOSE)
            )

        val logger = MessageCollectorBackedKaptLogger(
          optionsBuilder.flags.contains(KaptFlag.VERBOSE),
          optionsBuilder.flags.contains(KaptFlag.INFO_AS_WARNINGS),
          messageCollector
        )

        fun abortAnalysis() = FirAnalysisHandlerExtension.Companion.registerExtension(AbortAnalysisHandlerExtension())
        if (!optionsBuilder.checkOptions(logger, configuration, ::abortAnalysis)) {
            return
        }

        val options = optionsBuilder.build()

        options.sourcesOutputDir.mkdirs()

        if (options[KaptFlag.VERBOSE]) {
            logger.info(options.logString())
        }

        configuration.put(KAPT_OPTIONS, optionsBuilder)

        val kaptFirAnalysisCompletedHandlerExtension =
            object : FirKaptAnalysisHandlerExtension(logger) {
                override fun loadProcessors() = LoadedProcessors(
                  processors = processors,
                  classLoader = this::class.java.classLoader
                )
            }

        FirAnalysisHandlerExtension.Companion.registerExtension(kaptFirAnalysisCompletedHandlerExtension)
    }

    private fun KaptOptions.Builder.checkOptions(
      logger: KaptLogger,
      configuration: CompilerConfiguration,
      abortAnalysis: () -> Unit
    ): Boolean {
        if (classesOutputDir == null) {
            if (configuration.get(JVMConfigurationKeys.OUTPUT_JAR) != null) {
                logger.error("Kapt does not support specifying JAR file outputs. Please specify the classes output directory explicitly.")
                abortAnalysis()
                return false
            }
            else {
                classesOutputDir = configuration.get(JVMConfigurationKeys.OUTPUT_DIRECTORY)
            }
        }

        if (sourcesOutputDir == null || classesOutputDir == null || stubsOutputDir == null) {
            if (mode != AptMode.WITH_COMPILATION) {
                val nonExistentOptionName = when {
                    sourcesOutputDir == null -> "Sources output directory"
                    classesOutputDir == null -> "Classes output directory"
                    stubsOutputDir == null -> "Stubs output directory"
                    else -> throw IllegalStateException()
                }
                val moduleName = configuration.get(CommonConfigurationKeys.MODULE_NAME)
                    ?: configuration.get(JVMConfigurationKeys.MODULES).orEmpty().joinToString()

                logger.warn("$nonExistentOptionName is not specified for $moduleName, skipping annotation processing")
                abortAnalysis()
            }
            return false
        }

        if (!Kapt.checkJavacComponentsAccess(logger)) {
            abortAnalysis()
            return false
        }

        return true
    }

    /* This extension simply disables both code analysis and code generation.
     * When aptOnly is true, and any of required kapt options was not passed, we just abort compilation by providing this extension.
     * */
    private class AbortAnalysisHandlerExtension : FirAnalysisHandlerExtension() {
        override fun doAnalysis(project: Project, configuration: CompilerConfiguration) = true
        override fun isApplicable(configuration: CompilerConfiguration) = true
    }

}