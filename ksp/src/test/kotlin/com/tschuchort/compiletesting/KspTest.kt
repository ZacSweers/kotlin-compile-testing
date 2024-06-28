package com.tschuchort.compiletesting

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.writeTo
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile.Companion.java
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import java.util.EnumSet
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.text.Typography.ellipsis
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.`when`

@RunWith(Parameterized::class)
class KspTest(private val useKSP2: Boolean) {
  companion object {
    private val DUMMY_KOTLIN_SRC =
      kotlin(
        "foo.bar.Dummy.kt",
        """
            class Dummy {}
        """
          .trimIndent(),
      )

    private val DUMMY_JAVA_SRC =
      java(
        "foo.bar.DummyJava.java",
        """
            class DummyJava {}
        """
          .trimIndent(),
      )

    @JvmStatic
    @Parameterized.Parameters(name = "useKSP2={0}")
    fun data(): Collection<Array<Any>> {
      return listOf(arrayOf(true), arrayOf(false))
    }
  }

  private fun newCompilation(): KotlinCompilation {
    return KotlinCompilation().apply {
      inheritClassPath = true
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
      kotlin(
        "TestAnnotation.kt",
        """
            package foo.bar
            annotation class TestAnnotation
        """
          .trimIndent(),
      )
    val targetClass =
      kotlin(
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
      kotlin(
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
      java(
        "JavaSubject.java",
        """
            @${SuppressWarnings::class.qualifiedName}("")
            class JavaSubject {}
            """
          .trimIndent(),
      )
    val kotlinSource =
      kotlin(
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

  class InheritedClasspathClass

  @Test
  fun findInheritedClasspathSymbols() {
    val javaSource =
      java(
        "JavaSubject.java",
        """
            import com.tschuchort.compiletesting.InheritedClasspathClass;            

            @${AutoService::class.qualifiedName}(Runnable.class)
            class JavaSubject {
              public InheritedClasspathClass create() {
                return new InheritedClasspathClass();
              }
            }
            """
          .trimIndent(),
      )
    val kotlinSource =
      kotlin(
        "KotlinSubject.kt",
        """
            import java.lang.Runnable
            import com.tschuchort.compiletesting.InheritedClasspathClass

            @${AutoService::class.qualifiedName}(Runnable::class)
            class KotlinSubject {
              fun create(): InheritedClasspathClass {
                return InheritedClasspathClass()
              }
            }
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

  // This test ensures that we can access files on the same source compilation as the test itself
  @Test
  fun inheritedSourceClasspath() {
    val source = kotlin(
      "Example.kt",
      """
        package test
        
        import com.tschuchort.compiletesting.ClasspathTestAnnotation
        import com.tschuchort.compiletesting.AnnotationEnumValue
        import com.tschuchort.compiletesting.AnotherAnnotation
        
        @ClasspathTestAnnotation(
          enumValue = AnnotationEnumValue.ONE,
          enumValueArray = [AnnotationEnumValue.ONE, AnnotationEnumValue.TWO],
          anotherAnnotation = AnotherAnnotation(""),
          anotherAnnotationArray = [AnotherAnnotation("Hello")]
        )
        class Example
      """
    )

    val compilation =
      newCompilation().apply {
        sources = listOf(source)
        symbolProcessorProviders += simpleProcessor { resolver, codeGenerator ->
          resolver
            .getSymbolsWithAnnotation(ClasspathTestAnnotation::class.java.canonicalName)
            .filterIsInstance<KSClassDeclaration>()
            .filterNot { !it.simpleName.asString().startsWith("Gen_") }
            .forEach {
              val annotation = it.annotations.first().toAnnotationSpec()
              FileSpec.get(
                it.packageName.asString(), TypeSpec.classBuilder("Gen_${it.simpleName.asString()}")
                  .addAnnotation(annotation)
                  .build()
              )
                .writeTo(
                  codeGenerator,
                  aggregating = false
                )
            }
        }
      }

    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
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
      kotlin(
        "TestAnnotation.kt",
        """
            package foo.bar
            annotation class TestAnnotation
        """
          .trimIndent(),
      )
    val targetClass =
      kotlin(
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
    assertThat(result.diagnosticMessages)
      .contains(DiagnosticMessage(DiagnosticSeverity.LOGGING, "This is a log message"))
    assertThat(result.messages).contains("This is an info message")
    assertThat(result.diagnosticMessages)
      .contains(DiagnosticMessage(DiagnosticSeverity.INFO, "This is an info message"))
    assertThat(result.messages).contains("This is an warn message")
    assertThat(result.diagnosticMessages)
      .contains(DiagnosticMessage(DiagnosticSeverity.WARNING, "This is an warn message"))
  }

  @Test
  fun loggingLevels() {
    val annotation =
      kotlin(
        "TestAnnotation.kt",
        """
            package foo.bar
            annotation class TestAnnotation
        """
          .trimIndent(),
      )
    val targetClass =
      kotlin(
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
          kspLoggingLevels =
            EnumSet.of(CompilerMessageSeverity.INFO, CompilerMessageSeverity.WARNING)
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
    assertThat(result.messages).contains("This is an info message")
    assertThat(result.diagnosticMessages)
      .contains(DiagnosticMessage(DiagnosticSeverity.INFO, "This is an info message"))
    assertThat(result.messages).contains("This is an warn message")
    assertThat(result.diagnosticMessages)
      .contains(DiagnosticMessage(DiagnosticSeverity.WARNING, "This is an warn message"))
  }

  @Test
  fun errorMessagesAreReadable() {
    val annotation =
      kotlin(
        "TestAnnotation.kt",
        """
            package foo.bar
            annotation class TestAnnotation
        """
          .trimIndent(),
      )
    val targetClass =
      kotlin(
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
    assertThat(result.diagnosticMessages)
      .contains(DiagnosticMessage(DiagnosticSeverity.ERROR, "This is an error message"))
    assertThat(result.messages).contains("This is a failure")
    assertThat(result.diagnosticMessages)
      // use contains on message as error includes stacktrace
      .usingElementComparator { a, b -> if (a.severity == b.severity && a.message.contains(b.message)) 0 else -1 }
      .contains(DiagnosticMessage(DiagnosticSeverity.ERROR, "This is a failure"))
  }

  @Test
  fun messagesAreEncodedAndDecodedWithUtf8() {
    val annotation =
      kotlin(
        "TestAnnotation.kt",
        """
            package foo.bar
            annotation class TestAnnotation
        """
          .trimIndent(),
      )
    val targetClass =
      kotlin(
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
    assertThat(result.diagnosticMessages)
      .contains(DiagnosticMessage(DiagnosticSeverity.LOGGING, "This is a log message with ellipsis $ellipsis"))
    assertThat(result.messages).contains("This is an info message with unicode \uD83D\uDCAB")
    assertThat(result.diagnosticMessages)
      .contains(DiagnosticMessage(DiagnosticSeverity.INFO, "This is an info message with unicode \uD83D\uDCAB"))
    assertThat(result.messages).contains("This is an warn message with emoji 🔥")
    assertThat(result.diagnosticMessages)
      .contains(DiagnosticMessage(DiagnosticSeverity.WARNING, "This is an warn message with emoji 🔥"))
  }

  // This test exercises both using withCompilation (for in-process compilation of generated
  // sources)
  // and generating Java sources (to ensure generated java files are compiled too)
  @Test
  fun withCompilationAndJavaTest() {
    val annotation =
      kotlin(
        "TestAnnotation.kt",
        """
            package foo.bar
            annotation class TestAnnotation
        """
          .trimIndent(),
      )
    val targetClass =
      kotlin(
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


  @Test
  fun `can filter messages by severity`() {
    val annotation =
      kotlin(
        "TestAnnotation.kt",
        """
            package foo.bar
            annotation class TestAnnotation
        """
          .trimIndent(),
      )
    val targetClass =
      kotlin(
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
    assertThat(result.messagesWithSeverity(DiagnosticSeverity.LOGGING)).contains("This is a log message")
    assertThat(result.messagesWithSeverity(DiagnosticSeverity.INFO)).contains("This is an info message")
    assertThat(result.messagesWithSeverity(DiagnosticSeverity.WARNING)).contains("This is an warn message")
  }
}
