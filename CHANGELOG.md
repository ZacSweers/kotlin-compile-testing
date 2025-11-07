Changelog
=========

**Unreleased**
--------------

- Update to Kotlin `2.3.0`.
- Remove `KEEP_KDOC_COMMENTS_IN_STUBS` kapt option as it was removed upstream.
- Remove `supportsK2` entirely, only K2 is supported going forward.

0.11.0
------

_2025-10-22_

- **Fix**: Feed generated `.java` files from KSP into the subsequent kotlin compilation.
- Build against KSP `2.3.0`.

Special thanks to [@martinbirn](https://github.com/martinbirn) for contributing to this release!

0.10.1
------

_2025-10-05_

- **Fix:** Don't set `jvmDefault` automatically (previously defaulted to `JvmDefaultMode.DISABLE`).
- **Fix:** Use the new, non-deprecated `jvmDefaultStable` CLI option when setting `jvmDefault`.
- Update Okio to `3.16.1`.
- Build against KSP `2.2.20-2.0.4`.

0.10.0
------

_2025-10-05_

- Remove support for Kotlin `1.x`. Only Kotlin 2.x is supported now.
  - This includes removal of all legacy 1.x compiler APIs such as `ComponentRegistrar`.
- Remove support for KSP1. Only KSP2 is supported now.
- Changed `verbose` to be `false` by default.
- Build against KSP `2.2.20-2.0.3`.

These changes are in preparation for the upcoming Kotlin `2.3.0`, which will error on builds that detect `ComponentRegistrar` instances on the build classpath.

0.9.0
-----

_2025-09-11_

- Update to Kotlin `2.2.20`. This release requires `2.2.20` or later.
- Build against KSP `2.2.20-2.0.2`.

0.8.0
-----

_2025-07-02_

- Update classgraph to `4.8.180`.
- Update okio to `3.15.0`.
- Update to Kotlin `2.2.0`.
- Update to KSP `2.2.0-2.0.2`.
- Remove KAPT4 support as this was removed in Kotlin 2.2.
- Support K2 KAPT.

Special thanks to [@eygraber](https://github.com/eygraber) for contributing to this release!

0.7.1
-----

_2025-05-05_

- Remove references to `useOldBackend`, which is removed in Kotlin `2.2.0`.

0.7.0
-----

_2024-11-28_

- Remove `irOnly` option from `KotlinJsCompilation`.
- Default to the current language/api version if one isn't specified in KSP2 invocations.
- Update to Kotlin `2.1.0`.
- Update to KSP `2.1.0-1.0.29`.

0.6.0
-----

_2024-11-11_

- **Enhancement**: Cleanup old sources between compilations.
- Update to Kotlin `2.0.21`.
- Update to KSP `2.0.21-1.0.27`. Note that this is now the minimum version of KSP support.
- Update classgraph to `4.8.177`.
- Update Okio to `3.9.1`.

Special thanks to [@ansman](https://github.com/ansman) for contributing to this release!

0.5.1
-----

_2024-06-05_

- **New**: Capture diagnostics with a severity level. This allows the output to be more easily filtered after the fact.

Special thanks to [@evant](https://github.com/evant) for contributing to this release!

0.5.0
-----

_2024-06-05_

- Update to Kotlin `2.0.0`.
- Update to KSP `2.0.0-1.0.22`.
- Change `supportsK2` to true by default.
- Change `disableStandardScript` to true by default. This doesn't seem to work reliably in K2 testing.
- Update kapt class location references.
- Support Kapt4 (AKA kapt.k2).
- Support KSP2.
- Introduce a new `KotlinCompilation.useKsp()` API to simplify KSP configuration.
- Update to ClassGraph `4.8.173`.

Note that in order to test Kapt 3 or KSP 1, you must now also set `languageVersion` to `1.9` in your `KotlinCompilation` configuration.

0.4.1
-----

_2024-03-25_

- **Fix**: Fix decoding of classloader resources.
- Update to Kotlin `1.9.23`.
- Update to KSP `1.9.2301.0.19`.
- Update to classgraph `4.8.168`.
- Update to Okio `3.9.0`.

Special thanks to [@jbarr21](https://github.com/jbarr21) for contributing to this release!

0.4.0
-----

_2023-10-31_

- **Enhancement**: Create parent directories of `SourceFile` in compilations.
- Update to Kotlin `1.9.20`.
- Update to KSP `1.9.20-1.0.13`.
- Update to ClassGraph `4.8.162`.
- Update to Okio `3.6.0`.

Special thanks to [@BraisGabin](https://github.com/BraisGabin) for contributing to this release!

0.3.2
-----

_2023-08-01_

- **Fix**: Include KSP-generated Java files in java compilation. This is particularly useful for KSP processors that generate Java code.
- **Enhancement**: Print full diagnostic messages when javac compilation fails, not just the cause. The cause message alone was often not very helpful.

0.3.1
-----

_2023-07-22_

- **Fix**: Set required `languageVersionSettings` property in `KspOptions`.
- Update to KSP `1.9.0-1.0.12`.
- Update to Okio `3.4.0`.

0.3.0
-----

_2023-07-06_

- **New**: Refactor results into common `CompilationResult` hierarchy.
- **Fix**: Missing UTF-8 encoding of logs resulting in unknown chars.
- **Fix**: Set resources path when compilerPluginRegistrars not empty.
- `useIR` is now enabled by default.
- Update to Kotlin `1.9.0`.
- Update to KSP `1.9.0-1.0.11`.

Special thanks to [@SimonMarquis](https://github.com/SimonMarquis) and [@bennyhuo](https://github.com/bennyhuo) for contributing to this release!

0.2.1
-----

_2023-01-09_

Happy new year!

- **New**: Expose the API to pass flags to KAPT. This is necessary in order to use KAPT's new JVM IR support.

0.2.0
-----

_2022-12-28_

- Deprecate `KotlinCompilation.singleModule` option as it no longer exists in kotlinc.
- Propagate `@ExperimentalCompilerApi` annotations
- `KotlinJsCompilation.irOnly` and `KotlinJsCompilation.irProduceJs` now default to true and are the only supported options.
- Expose new `KotlinCompilation.compilerPluginRegistrars` property for adding `CompilerPluginRegistrar` instances (the new entrypoint API for compiler plugins)
  ```kotlin
  KotlinCompilation().apply {
    compilerPluginRegistrars = listOf(MyCompilerPluginRegistrar())
  }
  ```
- Deprecate `KotlinCompilation.compilerPlugins` in favor of `KotlinCompilation.componentRegistrars`. The latter is also deprecated, but this is at least a clearer name.
  ```diff
  KotlinCompilation().apply {
  -  compilerPlugins = listOf(MyComponentRegistrar())
  +  componentRegistrars = listOf(MyComponentRegistrar())
  }
  ```
- Don't try to set removed kotlinc args. If they're removed, they're removed forever. This library will just track latest kotlin releases with its own.
- Dependency updates:
  ```
  Kotlin (and its associated artifacts) 1.8.0
  KSP 1.8.0
  Classgraph: 4.8.153
  ```

Special thanks to [@bnorm](https://github.com/bnorm) for contributing to this release.

0.1.0
-----

_2022-12-01_

Initial release. Changes from the original repo are as follows

Base commit: https://github.com/tschuchortdev/kotlin-compile-testing/commit/4f394fe485a0d6e0ed438dd9ce140b172b1bd746

- **New**: Add `supportsK2` option to `KotlinCompilation` to allow testing the new K2 compiler.
- Update to Kotlin `1.7.22`.
- Update to KSP `1.7.22-1.0.8`.
