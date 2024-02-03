package com.facebook.buck.jvm.java.javax.com.tschuchort.compiletesting

import com.tschuchort.compiletesting.KotlinCompilation
import java.io.File
import java.io.PrintStream
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * A standalone tool that can be run before the KotlinCompilation begins.
 */
@ExperimentalCompilerApi
fun interface PrecursorTool {
  fun execute(
    compilation: KotlinCompilation,
    output: PrintStream,
    sources: List<File>,
  ): KotlinCompilation.ExitCode
}
