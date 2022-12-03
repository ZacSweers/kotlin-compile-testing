package com.tschuchort.compiletesting

import io.github.classgraph.ClassGraph
import org.assertj.core.api.Assertions
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File

@OptIn(ExperimentalCompilerApi::class)
fun defaultCompilerConfig(): KotlinCompilation {
    return KotlinCompilation( ).apply {
        inheritClassPath = false
        correctErrorTypes = true
        verbose = true
        reportOutputFiles = false
        messageOutputStream = System.out
    }
}

@OptIn(ExperimentalCompilerApi::class)
fun defaultJsCompilerConfig(): KotlinJsCompilation {
    return KotlinJsCompilation( ).apply {
        inheritClassPath = false
        verbose = true
        reportOutputFiles = false
        messageOutputStream = System.out
    }
}


@OptIn(ExperimentalCompilerApi::class)
fun assertClassLoadable(compileResult: KotlinCompilation.Result, className: String): Class<*> {
    return try {
        val clazz = compileResult.classLoader.loadClass(className)
        Assertions.assertThat(clazz).isNotNull
        clazz
    }
    catch(e: ClassNotFoundException) {
        Assertions.fail<Nothing>("Class $className could not be loaded")
    }
}

/**
 * Returns the classpath for a dependency (format $name-$version).
 * This is necessary to know the actual location of a dependency
 * which has been included in test runtime (build.gradle).
 */
fun classpathOf(dependency: String): File {
    val regex = Regex(".*$dependency\\.jar")
    return ClassGraph().classpathFiles.first { classpath -> classpath.name.matches(regex) }
}
