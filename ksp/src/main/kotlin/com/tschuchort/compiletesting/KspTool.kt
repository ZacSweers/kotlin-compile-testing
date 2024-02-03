package com.tschuchort.compiletesting

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

internal interface KspTool {
  var symbolProcessorProviders: List<SymbolProcessorProvider>
  var processorOptions: Map<String, String>
  var incremental: Boolean
  var incrementalLog: Boolean
  var allWarningsAsErrors: Boolean
  var withCompilation: Boolean
}

/** Gets or creates the [KspTool] if it doesn't exist. */
@OptIn(ExperimentalCompilerApi::class)
internal fun KotlinCompilation.getKspTool(): KspTool {
  val ksp2Tool = precursorTools["ksp2"] as? Ksp2PrecursorTool?
  return ksp2Tool ?: getKspRegistrar()
}
