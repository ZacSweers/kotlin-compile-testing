dependencyResolutionManagement {
    versionCatalogs {
        if (System.getenv("DEP_OVERRIDES") == "true") {
            val overrides = System.getenv().filterKeys { it.startsWith("DEP_OVERRIDE_") }
            maybeCreate("libs").apply {
                for ((key, value) in overrides) {
                    val catalogKey = key.removePrefix("DEP_OVERRIDE_").lowercase()
                    println("Overriding $catalogKey with $value")
                    version(catalogKey, value)
                }
            }
        }
    }

    // Non-delegate APIs are annoyingly not public so we have to use withGroovyBuilder
    fun hasProperty(key: String): Boolean {
        return settings.withGroovyBuilder {
            "hasProperty"(key) as Boolean
        }
    }

    repositories {
        // Repos are declared roughly in order of likely to hit.

        // Snapshots/local go first in order to pre-empty other repos that may contain unscrupulous
        // snapshots.
        if (hasProperty("kct.config.enableSnapshots")) {
            maven("https://oss.sonatype.org/content/repositories/snapshots")
            maven("https://androidx.dev/snapshots/latest/artifacts/repository")
        }

        if (hasProperty("kct.config.enableMavenLocal")) {
            mavenLocal()
        }

        mavenCentral()

        google()

        maven("https://www.jetbrains.com/intellij-repository/releases") {
            name = "Intellij"
        }

        maven("https://cache-redirector.jetbrains.com/intellij-dependencies") {
            name = "Intellij"
        }
    }
}

pluginManagement {
    // Non-delegate APIs are annoyingly not public so we have to use withGroovyBuilder
    fun hasProperty(key: String): Boolean {
        return settings.withGroovyBuilder {
            "hasProperty"(key) as Boolean
        }
    }

    repositories {
        // Repos are declared roughly in order of likely to hit.

        // Snapshots/local go first in order to pre-empty other repos that may contain unscrupulous
        // snapshots.
        if (hasProperty("kct.config.enableSnapshots")) {
            maven("https://oss.sonatype.org/content/repositories/snapshots")
            maven("https://androidx.dev/snapshots/latest/artifacts/repository")
        }

        if (hasProperty("kct.config.enableMavenLocal")) {
            mavenLocal()
        }

        mavenCentral()

        google()

        // Gradle's plugin portal proxies jcenter, which we don't want. To avoid this, we specify
        // exactly which dependencies to pull from here.
        exclusiveContent {
            forRepository(::gradlePluginPortal)
            filter {
                includeModule("com.gradle", "gradle-enterprise-gradle-plugin")
                includeModule("com.gradle.enterprise", "com.gradle.enterprise.gradle.plugin")
                includeModule("com.diffplug.spotless", "com.diffplug.spotless.gradle.plugin")
                includeModule("org.gradle.kotlin.kotlin-dsl", "org.gradle.kotlin.kotlin-dsl.gradle.plugin")
                includeModule("org.gradle.kotlin", "gradle-kotlin-dsl-plugins")
                includeModule("com.github.gmazzo.buildconfig", "com.github.gmazzo.buildconfig.gradle.plugin")
                includeModule("com.github.gmazzo", "gradle-buildconfig-plugin")
            }
        }
    }
    plugins { id("com.gradle.enterprise") version "3.17.4" }
}

rootProject.name = "kotlin-compile-testing"
include("ksp")
include("core")

// https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:type-safe-project-accessors
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")