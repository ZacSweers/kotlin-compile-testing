package com.tschuchort.compiletesting

import io.github.classgraph.ClassGraph
import java.io.File
import org.assertj.core.api.Assertions

fun defaultCompilerConfig(useK2: Boolean): KotlinCompilation {
    return KotlinCompilation().apply {
        inheritClassPath = false
        correctErrorTypes = true
        verbose = true
        reportOutputFiles = false
        messageOutputStream = System.out
        if (useK2) {
            languageVersion = "2.0"
            useKapt4 = true
        } else {
            languageVersion = "1.9"
        }
    }
}

fun defaultJsCompilerConfig(): KotlinJsCompilation {
    return KotlinJsCompilation( ).apply {
        inheritClassPath = false
        verbose = true
        reportOutputFiles = false
        messageOutputStream = System.out
        moduleName = "test"
    }
}


fun assertClassLoadable(compileResult: JvmCompilationResult, className: String): Class<*> {
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
