package com.tschuchort.compiletesting

import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import okio.Buffer
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.arguments.validateArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.kapt3.base.KaptOptions
import org.jetbrains.kotlin.util.ServiceLoaderLite

/**
 * Base compilation class for sharing common compiler arguments and
 * functionality. Should not be used outside of this library as it is an
 * implementation detail.
 */
@ExperimentalCompilerApi
abstract class AbstractKotlinCompilation<A : CommonCompilerArguments> internal constructor() {
    /** Working directory for the compilation */
    var workingDir: File by default {
        val path = Files.createTempDirectory("Kotlin-Compilation")
        log("Created temporary working directory at ${path.toAbsolutePath()}")
        return@default path.toFile()
    }

    /**
     * Paths to directories or .jar files that contain classes
     * to be made available in the compilation (i.e. added to
     * the classpath)
     */
    var classpaths: List<File> = emptyList()

    /**
     * Paths to plugins to be made available in the compilation
     */
    var pluginClasspaths: List<File> = emptyList()

    @Deprecated(
        "Use componentRegistrars instead",
        ReplaceWith("componentRegistrars"),
        DeprecationLevel.ERROR
    )
    var compilerPlugins: List<ComponentRegistrar> = emptyList()

    /**
     * Legacy [ComponentRegistrar] plugins that should be added to the compilation.
     */
    @Deprecated("Migrate to ComponentPluginRegistrar and use compilerPluginRegistrars instead")
    var componentRegistrars: List<ComponentRegistrar> = emptyList()

    /**
     * [CompilerPluginRegistrars][CompilerPluginRegistrar] that should be added to the compilation.
     */
    var compilerPluginRegistrars: List<CompilerPluginRegistrar> = emptyList()

    /**
     * Commandline processors for compiler plugins that should be added to the compilation
     */
    var commandLineProcessors: List<CommandLineProcessor> = emptyList()

    /** Source files to be compiled */
    var sources: List<SourceFile> = emptyList()

    /** Print verbose logging info */
    var verbose: Boolean = true

    /**
     * Helpful information (if [verbose] = true) and the compiler
     * system output will be written to this stream
     */
    var messageOutputStream: OutputStream = System.out

    /** Inherit classpath from calling process */
    var inheritClassPath: Boolean = false

    /** Suppress all warnings */
    var suppressWarnings: Boolean = false

    /** All warnings should be treated as errors */
    var allWarningsAsErrors: Boolean = false

    /** Report locations of files generated by the compiler */
    var reportOutputFiles: Boolean by default { verbose }

    /** Report on performance of the compilation */
    var reportPerformance: Boolean = false

    var languageVersion: String? = null
    var apiVersion: String? = null

    /** Enable experimental multiplatform support */
    var multiplatform: Boolean = false

    /** Do not check presence of 'actual' modifier in multi-platform projects */
    var noCheckActual: Boolean = false

    /** Enable usages of API that requires opt-in with an opt-in requirement marker with the given fully qualified name */
    var optIn: List<String>? = null

    /** Additional string arguments to the Kotlin compiler */
    var kotlincArguments: List<String> = emptyList()

    /** Options to be passed to compiler plugins: -P plugin:<pluginId>:<optionName>=<value>*/
    var pluginOptions: List<PluginOption> = emptyList()

    /**
     * Path to the kotlin-stdlib-common.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    var kotlinStdLibCommonJar: File? by default {
        HostEnvironment.kotlinStdLibCommonJar
    }

    /** Enable support for the new K2 compiler. */
    var supportsK2 = true

    /** Disables compiler scripting support. */
    var disableStandardScript = true

    /** Tools that need to run before the compilation. */
    val precursorTools: MutableMap<String, PrecursorTool> = mutableMapOf()

    protected val extraGeneratedSources = mutableListOf<File>()

    /** Registers extra directories with generated sources, such as sources generated by KSP. */
    fun registerGeneratedSourcesDir(dir: File) {
        extraGeneratedSources.add(dir)
    }

    // Directory for input source files
    protected val sourcesDir get() = workingDir.resolve("sources")

    protected fun commonArguments(args: A, configuration: (args: A) -> Unit): A {
        args.pluginClasspaths = pluginClasspaths.map(File::getAbsolutePath).toTypedArray()

        args.verbose = verbose

        args.suppressWarnings = suppressWarnings
        args.allWarningsAsErrors = allWarningsAsErrors
        args.reportOutputFiles = reportOutputFiles
        args.reportPerf = reportPerformance
        args.multiPlatform = multiplatform
        args.noCheckActual = noCheckActual
        args.optIn = optIn?.toTypedArray()

        if (languageVersion != null) {
            args.languageVersion = this.languageVersion
        }

        if (apiVersion != null) {
            args.apiVersion = this.apiVersion
        }

        configuration(args)

        /**
         * It's not possible to pass dynamic [CommandLineProcessor] instances directly to the [K2JSCompiler]
         * because the compiler discovers them on the classpath through a service locator, so we need to apply
         * the same trick as with [ComponentRegistrar]s: We put our own static [CommandLineProcessor] on the
         * classpath which in turn calls the user's dynamic [CommandLineProcessor] instances.
         */
        MainCommandLineProcessor.threadLocalParameters.set(
            MainCommandLineProcessor.ThreadLocalParameters(commandLineProcessors)
        )

        /**
         * Our [MainCommandLineProcessor] only has access to the CLI options that belong to its own plugin ID.
         * So in order to be able to access CLI options that are meant for other [CommandLineProcessor]s we
         * wrap these CLI options, send them to our own plugin ID and later unwrap them again to forward them
         * to the correct [CommandLineProcessor].
         */
        args.pluginOptions = pluginOptions.map { (pluginId, optionName, optionValue) ->
            "plugin:${MainCommandLineProcessor.pluginId}:${MainCommandLineProcessor.encodeForeignOptionName(pluginId, optionName)}=$optionValue"
        }.toTypedArray()

        /* Parse extra CLI arguments that are given as strings so users can specify arguments that are not yet
        implemented here as well-typed properties. */
        parseCommandLineArguments(kotlincArguments, args)

        validateArguments(args.errors)?.let {
            throw IllegalArgumentException("Errors parsing kotlinc CLI arguments:\n$it")
        }

        return args
    }

    /** Performs the compilation step to compile Kotlin source files */
    protected fun compileKotlin(sources: List<File>, compiler: CLICompiler<A>, arguments: A): KotlinCompilation.ExitCode {
        if (this is KotlinCompilation) {
            // Execute precursor tools first
            for (tool in precursorTools) {
                val exitCode = try {
                    tool.value.execute(this, internalMessageStream, sources)
                } catch (t: Throwable) {
                    t.message?.let { internalMessageStream.println(it) }
                    t.printStackTrace(internalMessageStream)
                    KotlinCompilation.ExitCode.INTERNAL_ERROR
                }
                if (exitCode != KotlinCompilation.ExitCode.OK) {
                    return exitCode
                }
            }
        }

        // Update sources to include any generated in a precursor tool
        val generatedSourceFiles = extraGeneratedSources.flatMap(File::listFilesRecursively).filter(File::hasKotlinFileExtension)
            .map { it.absoluteFile }
        val finalSources = sources + generatedSourceFiles

        /**
         * Here the list of compiler plugins is set
         *
         * To avoid that the annotation processors are executed twice,
         * the list is set to empty
         */
        MainComponentRegistrar.threadLocalParameters.set(
            MainComponentRegistrar.ThreadLocalParameters(
                listOf(),
                KaptOptions.Builder(),
                componentRegistrars,
                compilerPluginRegistrars,
                supportsK2
            )
        )

        // in this step also include source files generated by kapt in the previous step
        val args = arguments.also { args ->
            args.freeArgs =
                finalSources.map(File::getAbsolutePath).distinct() + if (finalSources.none(File::hasKotlinFileExtension)) {
                    /* __HACK__: The Kotlin compiler expects at least one Kotlin source file or it will crash,
                       so we trick the compiler by just including an empty .kt-File. We need the compiler to run
                       even if there are no Kotlin files because some compiler plugins may also process Java files. */
                    listOf(SourceFile.new("emptyKotlinFile.kt", "").writeIfNeeded(sourcesDir).absolutePath)
                } else {
                    emptyList()
                }
            args.pluginClasspaths = (args.pluginClasspaths ?: emptyArray()) +
                    /** The resources path contains the MainComponentRegistrar and MainCommandLineProcessor which will
                     be found by the Kotlin compiler's service loader. We add it only when the user has actually given
                     us ComponentRegistrar instances to be loaded by the MainComponentRegistrar because the experimental
                     K2 compiler doesn't support plugins yet. This way, users of K2 can prevent MainComponentRegistrar
                     from being loaded and crashing K2 by setting both [componentRegistrars] and [commandLineProcessors] to
                     the emptyList. */
                    if (
                        componentRegistrars.isNotEmpty() ||
                        compilerPluginRegistrars.isNotEmpty() ||
                        commandLineProcessors.isNotEmpty()
                    ) arrayOf(getResourcesPath())
                    else emptyArray()
        }

        val compilerMessageCollector = PrintingMessageCollector(
            internalMessageStream, MessageRenderer.GRADLE_STYLE, verbose
        )

        return convertKotlinExitCode(
            compiler.exec(compilerMessageCollector, Services.EMPTY, args)
        )
    }

    protected fun getResourcesPath(): String {
        return this::class.java.classLoader.getResources(resourceName)
            .asSequence()
            .mapNotNull { url -> urlToResourcePath(url) }
            .find { resourcesPath ->
                ServiceLoaderLite.findImplementations(ComponentRegistrar::class.java, listOf(resourcesPath.toFile()))
                    .any { implementation -> implementation == MainComponentRegistrar::class.java.name }
            }?.toString() ?: throw AssertionError("Could not get path to ComponentRegistrar service from META-INF")
    }

    /** Maps a URL resource for a class from a JAR or file to an absolute Path on disk  */
    internal fun urlToResourcePath(url: URL): Path? {
        val uri = url.toURI()
        val uriPath = when (uri.scheme) {
            "jar" -> uri.rawSchemeSpecificPart.removeSuffix("!/$resourceName")
            "file" -> uri.toString().removeSuffix("/$resourceName")
            else -> return null
        }
        return Paths.get(URI.create(uriPath)).toAbsolutePath()
    }

    /** Searches compiler log for known errors that are hard to debug for the user */
    protected fun searchSystemOutForKnownErrors(compilerSystemOut: String) {
        if (compilerSystemOut.contains("No enum constant com.sun.tools.javac.main.Option.BOOT_CLASS_PATH")) {
            warn(
                "${this::class.simpleName} has detected that the compiler output contains an error message that may be " +
                        "caused by including a tools.jar file together with a JDK of version 9 or later. " +
                        if (inheritClassPath)
                            "Make sure that no tools.jar (or unwanted JDK) is in the inherited classpath"
                        else ""
            )
        }

        if (compilerSystemOut.contains("Unable to find package java.")) {
            warn(
                "${this::class.simpleName} has detected that the compiler output contains an error message " +
                        "that may be caused by a missing JDK. This can happen if jdkHome=null and inheritClassPath=false."
            )
        }
    }

    protected val hostClasspaths by lazy { HostEnvironment.classpath }

    /* This internal buffer and stream is used so it can be easily converted to a string
    that is put into the [Result] object, in addition to printing immediately to the user's
    stream. */
    protected val internalMessageBuffer = Buffer()
    protected val internalMessageStream = PrintStream(
        TeeOutputStream(
            object : OutputStream() {
                override fun write(b: Int) = messageOutputStream.write(b)
                override fun write(b: ByteArray) = messageOutputStream.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) = messageOutputStream.write(b, off, len)
                override fun flush() = messageOutputStream.flush()
                override fun close() = messageOutputStream.close()
            },
            internalMessageBuffer.outputStream()
        ),
        /* autoFlush = */ false,
        /* encoding = */ "UTF-8",
    )

    protected fun log(s: String) {
        if (verbose)
            internalMessageStream.println("logging: $s")
    }

    protected fun warn(s: String) = internalMessageStream.println("warning: $s")
    protected fun error(s: String) = internalMessageStream.println("error: $s")

    internal val internalMessageStreamAccess: PrintStream get() = internalMessageStream

    private val resourceName = "META-INF/services/org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar"
}

@ExperimentalCompilerApi
internal fun convertKotlinExitCode(code: ExitCode) = when(code) {
    ExitCode.OK -> KotlinCompilation.ExitCode.OK
    ExitCode.OOM_ERROR -> throw OutOfMemoryError("Kotlin compiler ran out of memory")
    ExitCode.INTERNAL_ERROR -> KotlinCompilation.ExitCode.INTERNAL_ERROR
    ExitCode.COMPILATION_ERROR -> KotlinCompilation.ExitCode.COMPILATION_ERROR
    ExitCode.SCRIPT_EXECUTION_ERROR -> KotlinCompilation.ExitCode.SCRIPT_EXECUTION_ERROR
}
