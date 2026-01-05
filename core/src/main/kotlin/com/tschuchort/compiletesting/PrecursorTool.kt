package com.tschuchort.compiletesting

import java.io.File
import java.io.PrintStream
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/** A standalone tool that can be run before the KotlinCompilation begins. */
@ExperimentalCompilerApi
fun interface PrecursorTool {
  fun execute(
    compilation: KotlinCompilation,
    output: PrintStream,
    sources: List<File>,
  ): KotlinCompilation.ExitCode
}
