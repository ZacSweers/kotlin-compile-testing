import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessExtensionPredeclare
import com.diffplug.spotless.LineEnding
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.dokka)
  alias(libs.plugins.mavenPublish) apply false
  alias(libs.plugins.spotless)
}

dokka {
  dokkaPublications.html {
    outputDirectory.set(rootDir.resolve("docs/api/0.x"))
    includes.from(project.layout.projectDirectory.file("README.md"))
  }
}

val ktfmtVersion = libs.versions.ktfmt.get()

spotless { predeclareDeps() }

configure<SpotlessExtensionPredeclare> {
  kotlin { ktfmt(ktfmtVersion).googleStyle().configure { it.setRemoveUnusedImports(true) } }
  kotlinGradle { ktfmt(ktfmtVersion).googleStyle().configure { it.setRemoveUnusedImports(true) } }
  java {
    googleJavaFormat(libs.versions.gjf.get())
      .reorderImports(true)
      .reflowLongStrings(true)
      .reorderImports(true)
  }
}

// Configure spotless in subprojects
allprojects {
  apply(plugin = "com.diffplug.spotless")
  configure<SpotlessExtension> {
    setLineEndings(LineEnding.GIT_ATTRIBUTES_FAST_ALLSAME)
    format("misc") {
      target("*.gradle", "*.md", ".gitignore")
      trimTrailingWhitespace()
      leadingTabsToSpaces(2)
      endWithNewline()
    }
    java {
      googleJavaFormat(libs.versions.gjf.get())
        .reorderImports(true)
        .reflowLongStrings(true)
        .reorderImports(true)
      target("src/**/*.java")
      trimTrailingWhitespace()
      endWithNewline()
      targetExclude("**/spotless.java")
      targetExclude("**/src/test/data/**")
      targetExclude("**/*Generated.java")
    }
    kotlin {
      ktfmt(ktfmtVersion).googleStyle().configure { it.setRemoveUnusedImports(true) }
      target("src/**/*.kt")
      trimTrailingWhitespace()
      endWithNewline()
      targetExclude("**/spotless.kt")
      targetExclude("**/src/test/data/**")
    }
    kotlinGradle {
      ktfmt(ktfmtVersion).googleStyle().configure { it.setRemoveUnusedImports(true) }
      target("*.kts")
      trimTrailingWhitespace()
      endWithNewline()
    }
  }
}

subprojects {
  pluginManager.withPlugin("java") {
    configure<JavaPluginExtension> { toolchain { languageVersion.set(JavaLanguageVersion.of(23)) } }

    tasks.withType<JavaCompile>().configureEach { options.release.set(8) }
  }

  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    tasks.withType<KotlinCompile>().configureEach {
      compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        progressiveMode.set(true)
      }
    }
  }

  if (JavaVersion.current() >= JavaVersion.VERSION_16) {
    tasks.withType<Test>().configureEach {
      jvmArgs(
        "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
      )
    }
  }

  pluginManager.withPlugin("com.vanniktech.maven.publish") {
    apply(plugin = "org.jetbrains.dokka")

    configure<DokkaExtension> {
      basePublicationsDirectory.set(layout.buildDirectory.dir("dokkaDir"))
      dokkaSourceSets.configureEach { skipDeprecated.set(true) }
    }

    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(automaticRelease = true)
      signAllPublications()
    }
  }
}

dependencies {
  dokka(projects.core)
  dokka(projects.ksp)
}
