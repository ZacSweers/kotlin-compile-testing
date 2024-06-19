/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.buck.jvm.java.javax.com.tschuchort.compiletesting

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity

public enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO,
    LOGGING,
}

/**
 * Holder for diagnostics messages
 */
public data class DiagnosticMessage(
    val severity: DiagnosticSeverity,
    val message: String,
)

internal fun CompilerMessageSeverity.toSeverity() = when (this) {
    CompilerMessageSeverity.EXCEPTION,
    CompilerMessageSeverity.ERROR -> DiagnosticSeverity.ERROR
    CompilerMessageSeverity.STRONG_WARNING,
    CompilerMessageSeverity.WARNING -> DiagnosticSeverity.WARNING
    CompilerMessageSeverity.INFO -> DiagnosticSeverity.INFO
    CompilerMessageSeverity.LOGGING,
    CompilerMessageSeverity.OUTPUT -> DiagnosticSeverity.LOGGING
}