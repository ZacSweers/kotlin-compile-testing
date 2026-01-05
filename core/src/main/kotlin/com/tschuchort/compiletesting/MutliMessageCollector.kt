package com.tschuchort.compiletesting

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

internal class MultiMessageCollector(private vararg val collectors: MessageCollector) :
  MessageCollector {

  override fun clear() {
    collectors.forEach { it.clear() }
  }

  override fun hasErrors(): Boolean {
    return collectors.any { it.hasErrors() }
  }

  override fun report(
    severity: CompilerMessageSeverity,
    message: String,
    location: CompilerMessageSourceLocation?,
  ) {
    collectors.forEach { it.report(severity, message, location) }
  }
}
