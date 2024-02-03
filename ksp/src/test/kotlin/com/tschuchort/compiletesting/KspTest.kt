package com.tschuchort.compiletesting

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.text.Typography.ellipsis
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`

class KspTest {
  companion object {
    private val DUMMY_KOTLIN_SRC =
      SourceFile.kotlin(
        "foo.bar.Dummy.kt",
        """
            class Dummy {}
        """
          .trimIndent(),
      )

    private val DUMMY_JAVA_SRC =
      SourceFile.java(
        "foo.bar.DummyJava.java",
        """
            class DummyJava {}
        """
          .trimIndent(),
      )
  }

  private val useKSP2 = System.getProperty("kct.test.useKsp2", "false").toBoolean()

  private fun newCompilation(): KotlinCompilation {
    return KotlinCompilation().apply {
      if (useKSP2) {
        useKsp2()
      } else {
        languageVersion = "1.9"
      }
    }
  }

  @Test
  fun failedKspTest() {
    val instance = mock<SymbolProcessor>()
    val providerInstance = mock<SymbolProcessorProvider>()
    `when`(providerInstance.create(any())).thenReturn(instance)
    `when`(instance.process(any())).thenThrow(RuntimeException("intentional fail"))
    val result =
      newCompilation()
        .apply {
          sources = listOf(DUMMY_KOTLIN_SRC)
          symbolProcessorProviders += providerInstance
        }
        .compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.INTERNAL_ERROR)
    assertThat(result.messages).contains("intentional fail")
  }

  @Test
  fun allProcessorMethodsAreCalled() {
    val instance = mock<SymbolProcessor>()
    val providerInstance = mock<SymbolProcessorProvider>()
    `when`(providerInstance.create(any())).thenReturn(instance)
    val result =
      newCompilation()
        .apply {
          sources = listOf(DUMMY_KOTLIN_SRC)
          symbolProcessorProviders += providerInstance
        }
        .compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    providerInstance.inOrder { verify().create(any()) }
    instance.inOrder {
      verify().process(any())
      verify().finish()
    }
  }

  @Test
  fun allProcessorMethodsAreCalledWhenOnlyJavaFilesArePresent() {
    val instance = mock<SymbolProcessor>()
    val providerInstance = mock<SymbolProcessorProvider>()
    `when`(providerInstance.create(any())).thenReturn(instance)
    val result =
      newCompilation()
        .apply {
          sources = listOf(DUMMY_JAVA_SRC)
          symbolProcessorProviders += providerInstance
        }
        .compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    providerInstance.inOrder { verify().create(any()) }
    instance.inOrder {
      verify().process(any())
      verify().finish()
    }
  }

  @Test
  fun processorGeneratedCodeIsVisible() {
    val annotation =
      SourceFile.kotlin(
        "TestAnnotation.kt",
        """
            package foo.bar
            annotation class TestAnnotation
        """
          .trimIndent(),
      )
    val targetClass =
      SourceFile.kotlin(
        "AppCode.kt",
        """
            package foo.bar
            import foo.bar.generated.AppCode_Gen
            @TestAnnotation
            class AppCode {
                init {
                    // access generated code
                    AppCode_Gen()
                }
            }
        """
          .trimIndent(),
      )
    val result =
      newCompilation()
        .apply {
          sources = listOf(annotation, targetClass)
          symbolProcessorProviders += SymbolProcessorProvider { env ->
            object : AbstractTestSymbolProcessor(env.codeGenerator) {
              override fun process(resolver: Resolver): List<KSAnnotated> {
                val symbols = resolver.getSymbolsWithAnnotation("foo.bar.TestAnnotation").toList()
                if (symbols.isNotEmpty()) {
                  assertThat(symbols.size).isEqualTo(1)
                  val klass = symbols.first()
                  check(klass is KSClassDeclaration)
                  val qName = klass.qualifiedName ?: error("should've found qualified name")
                  val genPackage = "${qName.getQualifier()}.generated"
                  val genClassName = "${qName.getShortName()}_Gen"
                  codeGenerator
                    .createNewFile(
                      dependencies = Dependencies.ALL_FILES,
                      packageName = genPackage,
                      fileName = genClassName,
                    )
                    .bufferedWriter()
                    .use {
                      it.write(
                        """
                            package $genPackage
                            class $genClassName() {}
                        """
                          .trimIndent()
                      )
                    }
                }
                return emptyList()
              }
            }
          }
        }
        .compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
  }

  @Test
  fun multipleProcessors() {
    // access generated code by multiple processors
    val source =
      SourceFile.kotlin(
        "foo.bar.Dummy.kt",
        """
            package foo.bar
            import generated.A
            import generated.B
            import generated.C
            class Dummy(val a:A, val b:B, val c:C)
        """
          .trimIndent(),
      )
    val result =
      newCompilation()
        .apply {
          sources = listOf(source)
          symbolProcessorProviders +=
            listOf(
              SymbolProcessorProvider { env ->
                ClassGeneratingProcessor(env.codeGenerator, "generated", "A")
              },
              SymbolProcessorProvider { env ->
                ClassGeneratingProcessor(env.codeGenerator, "generated", "B")
              },
              SymbolProcessorProvider { env ->
                ClassGeneratingProcessor(env.codeGenerator, "generated", "C")
              },
            )
        }
        .compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
  }

  @Test
  fun readProcessors() {
    val instance1 = mock<SymbolProcessorProvider>()
    val instance2 = mock<SymbolProcessorProvider>()
    newCompilation().apply {
      symbolProcessorProviders += instance1
      assertThat(symbolProcessorProviders).containsExactly(instance1)
      symbolProcessorProviders = mutableListOf(instance2)
      assertThat(symbolProcessorProviders).containsExactly(instance2)
      symbolProcessorProviders = (symbolProcessorProviders + instance1).toMutableList()
      assertThat(symbolProcessorProviders).containsExactly(instance2, instance1)
    }
  }

  @Test
  fun incremental() {
    newCompilation().apply {
      // Disabled by default
      assertThat(kspIncremental).isFalse()
      assertThat(kspIncrementalLog).isFalse()
      kspIncremental = true
      assertThat(kspIncremental).isTrue()
      kspIncrementalLog = true
      assertThat(kspIncrementalLog).isTrue()
    }
  }

  @Test
  fun outputDirectoryContents() {
    val compilation =
      newCompilation().apply {
        sources = listOf(DUMMY_KOTLIN_SRC)
        symbolProcessorProviders += SymbolProcessorProvider { env ->
          ClassGeneratingProcessor(env.codeGenerator, "generated", "Gen")
        }
      }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    val generatedSources = compilation.kspSourcesDir.walkTopDown().filter { it.isFile }.toList()
    assertThat(generatedSources)
      .containsExactly(compilation.kspSourcesDir.resolve("kotlin/generated/Gen.kt"))
  }

  @Test
  fun findSymbols() {
    val javaSource =
      SourceFile.java(
        "JavaSubject.java",
        """
            @${SuppressWarnings::class.qualifiedName}("")
            class JavaSubject {}
            """
          .trimIndent(),
      )
    val kotlinSource =
      SourceFile.kotlin(
        "KotlinSubject.kt",
        """
            @${SuppressWarnings::class.qualifiedName}("")
            class KotlinSubject {}
            """
          .trimIndent(),
      )
    val result = mutableListOf<String>()
    val compilation =
      newCompilation().apply {
        sources = listOf(javaSource, kotlinSource)
        symbolProcessorProviders += SymbolProcessorProvider { env ->
          object : AbstractTestSymbolProcessor(env.codeGenerator) {
            override fun process(resolver: Resolver): List<KSAnnotated> {
              resolver
                .getSymbolsWithAnnotation(SuppressWarnings::class.java.canonicalName)
                .filterIsInstance<KSClassDeclaration>()
                .forEach { result.add(it.qualifiedName!!.asString()) }
              return emptyList()
            }
          }
        }
      }
    compilation.compile()
    assertThat(result).containsExactlyInAnyOrder("JavaSubject", "KotlinSubject")
  }

  @Test
  fun findInheritedClasspathSymbols() {
    val javaSource =
      SourceFile.java(
        "JavaSubject.java",
        """
            @${AutoService::class.qualifiedName}(Runnable.class)
            class JavaSubject
            """
          .trimIndent(),
      )
    val kotlinSource =
      SourceFile.kotlin(
        "KotlinSubject.kt",
        """
            import java.lang.Runnable

            @${AutoService::class.qualifiedName}(Runnable::class)
            class KotlinSubject
            """
          .trimIndent(),
      )
    val result = mutableListOf<String>()
    val compilation =
      newCompilation().apply {
        sources = listOf(javaSource, kotlinSource)
        inheritClassPath = true
        symbolProcessorProviders += SymbolProcessorProvider { env ->
          object : AbstractTestSymbolProcessor(env.codeGenerator) {
            override fun process(resolver: Resolver): List<KSAnnotated> {
              resolver
                .getSymbolsWithAnnotation(AutoService::class.java.canonicalName)
                .filterIsInstance<KSClassDeclaration>()
                .forEach { result.add(it.qualifiedName!!.asString()) }
              return emptyList()
            }
          }
        }
      }
    compilation.compile()
    assertThat(result).containsExactlyInAnyOrder("JavaSubject", "KotlinSubject")
  }

  internal class ClassGeneratingProcessor(
    codeGenerator: CodeGenerator,
    private val packageName: String,
    private val className: String,
    times: Int = 1,
  ) : AbstractTestSymbolProcessor(codeGenerator) {
    val times = AtomicInteger(times)

    override fun process(resolver: Resolver): List<KSAnnotated> {
      super.process(resolver)
      if (times.decrementAndGet() == 0) {
        codeGenerator
          .createNewFile(
            dependencies = Dependencies.ALL_FILES,
            packageName = packageName,
            fileName = className,
          )
          .bufferedWriter()
          .use {
            it.write(
              """
                        package $packageName
                        class $className() {}
                        """
                .trimIndent()
            )
          }
      }
      return emptyList()
    }
  }

  @Test
  fun nonErrorMessagesAreReadable() {
    val annotation =
      SourceFile.kotlin(
        "TestAnnotation.kt",
        """
            package foo.bar
            annotation class TestAnnotation
        """
          .trimIndent(),
      )
    val targetClass =
      SourceFile.kotlin(
        "AppCode.kt",
        """
            package foo.bar
            @TestAnnotation
            class AppCode
        """
          .trimIndent(),
      )
    val result =
      newCompilation()
        .apply {
          sources = listOf(annotation, targetClass)
          symbolProcessorProviders += SymbolProcessorProvider { env ->
            object : AbstractTestSymbolProcessor(env.codeGenerator) {
              override fun process(resolver: Resolver): List<KSAnnotated> {
                env.logger.logging("This is a log message")
                env.logger.info("This is an info message")
                env.logger.warn("This is an warn message")
                return emptyList()
              }
            }
          }
        }
        .compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    assertThat(result.messages).contains("This is a log message")
    assertThat(result.messages).contains("This is an info message")
    assertThat(result.messages).contains("This is an warn message")
  }

  @Test
  fun errorMessagesAreReadable() {
    val annotation =
      SourceFile.kotlin(
        "TestAnnotation.kt",
        """
            package foo.bar
            annotation class TestAnnotation
        """
          .trimIndent(),
      )
    val targetClass =
      SourceFile.kotlin(
        "AppCode.kt",
        """
            package foo.bar
            @TestAnnotation
            class AppCode
        """
          .trimIndent(),
      )
    val result =
      newCompilation()
        .apply {
          sources = listOf(annotation, targetClass)
          symbolProcessorProviders += SymbolProcessorProvider { env ->
            object : AbstractTestSymbolProcessor(env.codeGenerator) {
              override fun process(resolver: Resolver): List<KSAnnotated> {
                env.logger.error("This is an error message")
                env.logger.exception(Throwable("This is a failure"))
                return emptyList()
              }
            }
          }
        }
        .compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("This is an error message")
    assertThat(result.messages).contains("This is a failure")
  }

  @Test
  fun messagesAreEncodedAndDecodedWithUtf8() {
    val annotation =
      SourceFile.kotlin(
        "TestAnnotation.kt",
        """
            package foo.bar
            annotation class TestAnnotation
        """
          .trimIndent(),
      )
    val targetClass =
      SourceFile.kotlin(
        "AppCode.kt",
        """
            package foo.bar
            @TestAnnotation
            class AppCode
        """
          .trimIndent(),
      )
    val result =
      newCompilation()
        .apply {
          sources = listOf(annotation, targetClass)
          symbolProcessorProviders += SymbolProcessorProvider { env ->
            object : AbstractTestSymbolProcessor(env.codeGenerator) {
              override fun process(resolver: Resolver): List<KSAnnotated> {
                env.logger.logging("This is a log message with ellipsis $ellipsis")
                env.logger.info("This is an info message with unicode \uD83D\uDCAB")
                env.logger.warn("This is an warn message with emoji 🔥")
                return emptyList()
              }
            }
          }
        }
        .compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    assertThat(result.messages).contains("This is a log message with ellipsis $ellipsis")
    assertThat(result.messages).contains("This is an info message with unicode \uD83D\uDCAB")
    assertThat(result.messages).contains("This is an warn message with emoji 🔥")
  }

  // This test exercises both using withCompilation (for in-process compilation of generated
  // sources)
  // and generating Java sources (to ensure generated java files are compiled too)
  @Test
  fun withCompilationAndJavaTest() {
    val annotation =
      SourceFile.kotlin(
        "TestAnnotation.kt",
        """
            package foo.bar
            annotation class TestAnnotation
        """
          .trimIndent(),
      )
    val targetClass =
      SourceFile.kotlin(
        "AppCode.kt",
        """
            package foo.bar
            @TestAnnotation
            class AppCode
        """
          .trimIndent(),
      )
    val compilation = newCompilation()
    val result =
      compilation
        .apply {
          sources = listOf(annotation, targetClass)
          symbolProcessorProviders += SymbolProcessorProvider { env ->
            object : AbstractTestSymbolProcessor(env.codeGenerator) {
              override fun process(resolver: Resolver): List<KSAnnotated> {
                resolver.getSymbolsWithAnnotation("foo.bar.TestAnnotation").forEach { symbol ->
                  check(symbol is KSClassDeclaration) { "Expected class declaration" }
                  @Suppress("DEPRECATION")
                  val simpleName = "${symbol.simpleName.asString().capitalize(Locale.US)}Dummy"
                  env.codeGenerator
                    .createNewFile(
                      dependencies = Dependencies.ALL_FILES,
                      packageName = "foo.bar",
                      fileName = simpleName,
                      extensionName = "java",
                    )
                    .bufferedWriter()
                    .use {
                      // language=JAVA
                      it.write(
                        """
                                        package foo.bar;
                                        
                                        class ${simpleName}Java {
                                        
                                        }
                                        """
                          .trimIndent()
                      )
                    }
                  env.codeGenerator
                    .createNewFile(
                      dependencies = Dependencies.ALL_FILES,
                      packageName = "foo.bar",
                      fileName = "${simpleName}Kt",
                      extensionName = "kt",
                    )
                    .bufferedWriter()
                    .use {
                      // language=KOTLIN
                      it.write(
                        """
                                        package foo.bar
                                        
                                        class ${simpleName}Kt {
                                        
                                        }
                                        """
                          .trimIndent()
                      )
                    }
                }
                return emptyList()
              }
            }
          }
          kspWithCompilation = true
        }
        .compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    assertThat(result.classLoader.loadClass("foo.bar.AppCodeDummyJava")).isNotNull()
    assertThat(result.classLoader.loadClass("foo.bar.AppCodeDummyKt")).isNotNull()
  }
}
