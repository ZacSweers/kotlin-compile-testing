package com.tschuchort.compiletesting

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.MockitoAdditionalMatchersKotlin.Companion.not
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement
import org.assertj.core.api.AbstractStringAssert
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class KotlinCompilationTests(
	private val useK2: Boolean
) {
	companion object {
		@Parameterized.Parameters(name = "useK2={0}")
		@JvmStatic
		fun data() = arrayOf(
			arrayOf(true),
			arrayOf(false)
		)
	}

	@Rule @JvmField val temporaryFolder = TemporaryFolder()

	val kotlinTestProc = KotlinTestProcessor()
	val javaTestProc = JavaTestProcessor()

	private fun AbstractStringAssert<*>.containsIgnoringCase(
		k1: String,
		k2: String,
	) {
		if (useK2) {
			containsIgnoringCase(k2)
		} else {
			containsIgnoringCase(k1)
		}
	}

	@Test
	fun `runs with only kotlin sources`() {
		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(SourceFile.kotlin("kSource.kt", "class KSource"))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "KSource")
	}

	@Test
	fun `runs with only java sources`() {
		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(SourceFile.java("JSource.java", "class JSource {}"))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "JSource")
	}

	@Test
	fun `runs with no sources`() {
		val result = defaultCompilerConfig(useK2).apply {
			sources = emptyList()
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `runs with SourceFile from path`() {
		val sourceFile = temporaryFolder.newFile("KSource.kt").apply {
			writeText("class KSource")
		}

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(SourceFile.fromPath(sourceFile))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "KSource")
	}

	@Test
	fun `runs with SourceFile from paths with filename conflicts`() {
		temporaryFolder.newFolder("a")
		val sourceFileA = temporaryFolder.newFile("a/KSource.kt").apply {
			writeText("package a\n\nclass KSource")
		}

		temporaryFolder.newFolder("b")
		val sourceFileB = temporaryFolder.newFile("b/KSource.kt").apply {
			writeText("package b\n\nclass KSource")
		}

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(
				SourceFile.fromPath(sourceFileA),
				SourceFile.fromPath(sourceFileB))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "a.KSource")
		assertClassLoadable(result, "b.KSource")
	}

	@Test
	fun `runs with sources in directory`() {
		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(SourceFile.kotlin("com/foo/bar/kSource.kt", """ 
					package com.foo.bar
					class KSource"""))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.foo.bar.KSource")
	}

	@Test
	fun `Kotlin can access JDK`() {
		val source = SourceFile.kotlin("kSource.kt", """
            import javax.lang.model.SourceVersion
            import java.io.File
            
            fun main(addKotlincArgs: Array<String>) {
            	File("")
            }
			""")

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(source)
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "KSourceKt")
	}

	@Test
	fun `Kotlin can not access JDK`() {
		val source = SourceFile.kotlin("kSource.kt", """
            import javax.lang.model.SourceVersion
            import java.io.File
            
            fun main(addKotlincArgs: Array<String>) {
            	File("")
            }
			""")

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(source)
			jdkHome = null

		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
		assertThat(result.messages).containsIgnoringCase(
			k1 = "unresolved reference: java",
			k2 = "Unresolved reference 'java'"
		)
	}

	@Test
	fun `can compile Kotlin without JDK`() {
		val source = SourceFile.kotlin("kSource.kt", "class KClass")

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(source)
			jdkHome = null
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "KClass")
	}

	@Test
	fun `Java can access JDK`() {
		val source = SourceFile.java("JSource.java", """
            import javax.lang.model.SourceVersion;
            import java.io.File;
            
            class JSource {
            	File foo() {
            		return new File("");
            	}
            }
			""")

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(source)
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "JSource")
	}

	@Test
	fun `Java can not access JDK`() {
		val source = SourceFile.java("JSource.java", """
		    import javax.lang.model.SourceVersion;
		    import java.io.File;
		    
		    class JSource {
		    	File foo() {
		    		return new File("");
		    	}
		    }
			""")

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(source)
			jdkHome = null
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
		assertThat(result.messages).contains("Unable to find package java.lang")
		assertThat(result.messages).contains(
			"jdkHome is set to null, removing boot classpath from java compilation"
		)
	}

	@Test
	fun `Java inherits classpath`() {
		val source = SourceFile.java("JSource.java", """
			package com.tschuchort.compiletesting;
		
			class JSource {
				void foo() {
					String s = KotlinCompilationTests.InheritedClass.class.getName();
				}
			}
				""")

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(source)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.JSource")
	}

	@Test
	fun `Java doesn't inherit classpath`() {
		val source = SourceFile.java("JSource.java", """
			package com.tschuchort.compiletesting;
		
			class JSource {
				void foo() {
					String s = KotlinCompilationTests.InheritedClass.class.getName();
				}
			}
				""")

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(source)
			inheritClassPath = false
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
		assertThat(result.messages).contains("package KotlinCompilationTests does not exist")
	}

	@Test
	fun `Kotlin inherits classpath`() {
		val source = SourceFile.kotlin("KSource.kt", """
			package com.tschuchort.compiletesting
		
			class KSource {
				fun foo() {
					val s = KotlinCompilationTests.InheritedClass::class.java.name
				}
			}
				""")


		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(source)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.KSource")
	}

	@Test
	fun `Kotlin doesn't inherit classpath`() {
		val source = SourceFile.kotlin("KSource.kt", """
			package com.tschuchort.compiletesting
		
			class KSource {
				fun foo() {
					val s = KotlinCompilationTests.InheritedClass::class.java.name
				}
			}
				""")


		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(source)
			inheritClassPath = false
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
		assertThat(result.messages).containsIgnoringCase(
			k1 = "unresolved reference: KotlinCompilationTests",
			k2 = "Unresolved reference 'KotlinCompilationTests'"
		)
	}

	@Test
	fun `Compiled Kotlin class can be loaded`() {
		val source = SourceFile.kotlin("Source.kt", """
			package com.tschuchort.compiletesting
		
			class Source {
				fun helloWorld(): String {
					return "Hello World"
				}
			}
				""")


		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(source)
			annotationProcessors = listOf(object : AbstractProcessor() {
				override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
					return false
				}
			})
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)

		val clazz = result.classLoader.loadClass("com.tschuchort.compiletesting.Source")
		assertThat(clazz).isNotNull

		val instance = clazz.newInstance()
		assertThat(instance).isNotNull

		assertThat(clazz).hasDeclaredMethods("helloWorld")
		assertThat(clazz.getDeclaredMethod("helloWorld").invoke(instance)).isEqualTo("Hello World")
	}

	@Test
	fun `Compiled Java class can be loaded`() {
		val source = SourceFile.java("Source.java", """
			package com.tschuchort.compiletesting;
		
			public class Source {
				public String helloWorld() {
					return "Hello World";
				}
			}
				""")


		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(source)
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)

		val clazz = result.classLoader.loadClass("com.tschuchort.compiletesting.Source")
		assertThat(clazz).isNotNull

		val instance = clazz.newInstance()
		assertThat(instance).isNotNull

		assertThat(clazz).hasDeclaredMethods("helloWorld")
		assertThat(clazz.getDeclaredMethod("helloWorld").invoke(instance)).isEqualTo("Hello World")
	}

	@Test
	fun `Kotlin can access Java class`() {
		val jSource = SourceFile.java("JSource.java", """
			package com.tschuchort.compiletesting;
		
			class JSource {
				void foo() {}
			}
				""")

				val kSource = SourceFile.kotlin("KSource.kt", """
			package com.tschuchort.compiletesting
		
			class KSource {
				fun bar() {
					JSource().foo()
				}
			}
				""")

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(kSource, jSource)
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.KSource")
		assertClassLoadable(result, "com.tschuchort.compiletesting.JSource")
	}

	@Test
	fun `Java can access Kotlin class`() {
		val jSource = SourceFile.java("JSource.java", """
			package com.tschuchort.compiletesting;
		
			class JSource {
				void foo() {
					String s = (new KSource()).bar();
				}
			}
				""")

				val kSource = SourceFile.kotlin("KSource.kt", """
			package com.tschuchort.compiletesting
		
			class KSource {
				fun bar(): String = "bar"
			}
				""")

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(kSource, jSource)
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.KSource")
		assertClassLoadable(result, "com.tschuchort.compiletesting.JSource")
	}

	@Test
	fun `Java AP sees Kotlin class`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
			
				@ProcessElem
				class KSource {
				}
					""")

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(javaTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.messages).contains(JavaTestProcessor.ON_INIT_MSG)

		assertThat(ProcessedElemMessage.parseAllIn(result.messages)).anyMatch {
			it.elementSimpleName == "KSource"
		}
	}

	@Test
	fun `Java AP sees Java class`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
			
				@ProcessElem
				class JSource {
				}
					""")

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(jSource)
			annotationProcessors = listOf(javaTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.messages).contains(JavaTestProcessor.ON_INIT_MSG)
		assertThat(result.diagnosticMessages)
			.contains(DiagnosticMessage(DiagnosticSeverity.WARNING, JavaTestProcessor.ON_INIT_MSG))

		assertThat(ProcessedElemMessage.parseAllIn(result.messages)).anyMatch {
			it.elementSimpleName == "JSource"
		}
	}

	@Test
	fun `Kotlin AP sees Kotlin class`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
			
				@ProcessElem
				class KSource {
				}
					"""
		)

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.messages).contains(KotlinTestProcessor.ON_INIT_MSG)

		assertThat(ProcessedElemMessage.parseAllIn(result.messages)).anyMatch {
			it.elementSimpleName == "KSource"
		}
	}

	@Test
	fun `Kotlin AP sees Java class`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
			
				@ProcessElem
				class JSource {
				}
					"""
		)

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(jSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.messages).contains(KotlinTestProcessor.ON_INIT_MSG)

		assertThat(ProcessedElemMessage.parseAllIn(result.messages)).anyMatch {
			it.elementSimpleName == "JSource"
		}
	}

	@Test
	fun `Given only Java sources, Kotlin sources are generated and compiled`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
			
				@ProcessElem
				class JSource {
				}
					"""
		)

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(jSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.messages).contains(KotlinTestProcessor.ON_INIT_MSG)

		val clazz = result.classLoader.loadClass(KotlinTestProcessor.GENERATED_PACKAGE +
				"." + KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME)
		assertThat(clazz).isNotNull
	}

	@Test
	fun `Java can access generated Kotlin class`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
				import ${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME};
			
				@ProcessElem
				class JSource {
					void foo() {
						Class<?> c = ${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME}.class;
					}
				}
					"""
		)

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(jSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.JSource")
		assertClassLoadable(result, "${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME}")
	}

	@Test
	fun `Java can access generated Java class`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
				import ${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME};
			
				@ProcessElem
				class JSource {
					void foo() {
						Class<?> c = ${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME}.class;
					}
				}
					"""
		)

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(jSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.JSource")
		assertClassLoadable(result, "${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME}")
	}

	@Test
	fun `Kotlin can access generated Kotlin class`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
				import ${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME}
			
				@ProcessElem
				class KSource {
					fun foo() {
						val c = ${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME}::class.java
					}
				}
					"""
		)

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.KSource")
		assertClassLoadable(result, "${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME}")
	}

	@Test
	fun `Kotlin can access generated Java class`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
				import ${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME}
			
				@ProcessElem
				class KSource {
					fun foo() {
						val c = ${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME}::class.java
					}
				}
				"""
		)

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.KSource")
		assertClassLoadable(result, "${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME}")
	}

	@Test
	fun `detects the plugin provided for compilation via pluginClasspaths property`() {
		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(SourceFile.kotlin("kSource.kt", "class KSource"))
			pluginClasspaths = listOf(classpathOf("kotlin-scripting-compiler-${KOTLIN_VERSION}"))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.messages).contains(
			"provided plugin org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar"
		)
	}

	@Test
	fun `returns an internal error when adding a non existing plugin for compilation`() {
		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(SourceFile.kotlin("kSource.kt", "class KSource"))
			pluginClasspaths = listOf(File("./non-existing-plugin.jar"))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.INTERNAL_ERROR)
		assertThat(result.messages).contains("non-existing-plugin.jar not found")
	}

	@Test
	fun `Generated Java source is among generated files list`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
			
				@ProcessElem
				class KSource {
					fun foo() {}
				}
				"""
		)

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.generatedFiles.map { it.name }).contains(KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME + ".java")
	}

	@Test
	fun `Generated Kotlin source is among generated files list`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
			
				@ProcessElem
				class KSource {
					fun foo() {}
				}
				"""
		)

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.generatedFiles.map { it.name }).contains(KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME + ".kt")
	}

	@Test
	fun `Compiled Kotlin class file is among generated files list`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
			
				@ProcessElem
				class KSource {
					fun foo() {}
				}
				"""
		)

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.generatedFiles.map { it.name }).contains("KSource.class")
	}

	@Test
	fun `Compiled Java class file is among generated files list`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
			
				@ProcessElem
				class JSource {
					void foo() {
					}
				}
					"""
		)

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(jSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.generatedFiles.map { it.name }).contains("JSource.class")
	}

	@Test
	fun `Custom plugin receives CLI argument`() {
	    val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting;
				class KSource()
			""".trimIndent()
		)

		val cliProcessor = spy(object : CommandLineProcessor {
			override val pluginId = "myPluginId"
			override val pluginOptions = listOf(CliOption("test_option_name", "", ""))
		})

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(kSource)
			inheritClassPath = false
			pluginOptions = listOf(PluginOption("myPluginId", "test_option_name", "test_value"))
			commandLineProcessors = listOf(cliProcessor)
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)

		verify(cliProcessor, atLeastOnce()).processOption(argWhere<AbstractCliOption> { it.optionName == "test_option_name" }, eq("test_value"), any())
		verify(cliProcessor, never()).processOption(argWhere<AbstractCliOption> { it.optionName == "test_option_name" }, not(eq("test_value")), any())
		verify(cliProcessor, never()).processOption(argWhere<AbstractCliOption> { it.optionName != "test_option_name" }, any(), any())
	}

	@Test
	fun `Output directory contains compiled class files`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
			
				@ProcessElem
				class JSource {
					void foo() {
					}
				}
					"""
		)

		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
			
				@ProcessElem
				class KSource {
					fun foo() {}
				}
				"""
		)

		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(jSource, kSource)
			annotationProcessors = emptyList()
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.outputDirectory.listFilesRecursively().map { it.name })
				.contains("JSource.class", "KSource.class")
	}

	@Test
	fun `java compilation runs in process when no jdk is specified`() {
		val source = SourceFile.java("JSource.java",
			"""
				class JSource {}
			""".trimIndent())
		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(source)
		}.compile()
		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.messages).contains(
			"jdkHome is not specified. Using system java compiler of the host process."
		)
		assertThat(result.messages).doesNotContain(
			"jdkHome is set to null, removing boot classpath from java compilation"
		)
	}

	@Ignore // Ignored because symlinks can't be created on Windows 7 without admin rights
	@Test
	fun `java compilation runs in a sub-process when jdk is specified`() {
		val source = SourceFile.java("JSource.java",
			"""
			class JSource {}
			""".trimIndent())
		val fakeJdkHome = temporaryFolder.newFolder("jdk-copy")
		fakeJdkHome.mkdirs()
		Files.createLink(fakeJdkHome.resolve("bin").toPath(), processJdkHome.toPath())
		val logsStream = ByteArrayOutputStream()
		val compiler = defaultCompilerConfig(useK2).apply {
			sources = listOf(source)
			jdkHome = fakeJdkHome
			messageOutputStream = logsStream
		}
		val result = compiler.compile()
		assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
		val logs = logsStream.toString("utf-8")// use string charset for jdk 8 compatibility
		assertThat(logs).contains(
			"compiling java in a sub-process because a jdkHome is specified"
		)
		assertThat(logs).doesNotContain(
			"jdkHome is set to null, removing boot classpath from java compilation"
		)
		fakeJdkHome.delete()
	}

	@Test
	fun `the classLoader from a 2nd compilation can load classes from the first compilation`() {
		val kSource1 = SourceFile.kotlin(
			"KSource1.kt", """
				package com.tschuchort.compiletesting
			
				interface KSource1
				"""
		)

		val result = defaultCompilerConfig(useK2)
			.apply {
				sources = listOf(kSource1)
				inheritClassPath = true
			}
			.compile()
			.apply {
				assertThat(exitCode).isEqualTo(ExitCode.OK)
				assertThat(outputDirectory.listFilesRecursively().map { it.name }).contains("KSource1.class")
			}


		val kSource2 = SourceFile.kotlin(
			"KSource2.kt", """
				package com.tschuchort.compiletesting
			
				interface KSource2 : KSource1
				"""
		)

		defaultCompilerConfig(useK2)
			.apply {
				sources = listOf(kSource2)
				inheritClassPath = true
			}
			.addPreviousResultToClasspath(result)
			.compile()
			.apply {
				assertThat(exitCode).isEqualTo(ExitCode.OK)
				assertThat(outputDirectory.listFilesRecursively().map { it.name }).contains("KSource2.class")
				assertThat(classLoader.loadClass("com.tschuchort.compiletesting.KSource2")).isNotNull
			}
	}

	@Test
	fun `runs the K2 compiler without compiler plugins`() {
		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(SourceFile.kotlin("kSource.kt", "class KSource"))
			componentRegistrars = emptyList()
			pluginClasspaths = emptyList()
			languageVersion = "2.0"
			inheritClassPath = true
			disableStandardScript = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "KSource")
	}

	@Ignore("This test is not set up correctly for K2 to work")
	@Test
	fun `can compile code with multi-platform expect modifier`() {
		val result = defaultCompilerConfig(useK2).apply {
			sources = listOf(
				SourceFile.kotlin("kSource1.kt", "expect interface MppInterface"),
				SourceFile.kotlin("kSource2.kt", "actual interface MppInterface")
			)
			multiplatform = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	class InheritedClass {}
}
