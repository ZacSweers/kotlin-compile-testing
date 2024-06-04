package com.tschuchort.compiletesting

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated

fun simpleProcessor(process: (resolver: Resolver, codeGenerator: CodeGenerator) -> Unit) =
  SymbolProcessorProvider { env ->
    object : SymbolProcessor {
      override fun process(resolver: Resolver): List<KSAnnotated> {
        process(resolver, env.codeGenerator)
        return emptyList()
      }
    }
  }

/** Helper class to write tests, only used in Ksp Compile Testing tests, not a public API. */
internal open class AbstractTestSymbolProcessor(protected val codeGenerator: CodeGenerator) :
  SymbolProcessor {
  override fun process(resolver: Resolver): List<KSAnnotated> {
    return emptyList()
  }
}
