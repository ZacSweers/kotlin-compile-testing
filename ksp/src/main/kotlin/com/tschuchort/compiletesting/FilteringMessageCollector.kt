package com.tschuchort.compiletesting

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

internal fun MessageCollector.filterBy(levels: Set<CompilerMessageSeverity>): MessageCollector {
  return FilteringMessageCollector(this) { it in levels }
}

private class FilteringMessageCollector(
  private val delegate: MessageCollector,
  private val filter: (CompilerMessageSeverity) -> Boolean,
) : MessageCollector by delegate {
  override fun report(
    severity: CompilerMessageSeverity,
    message: String,
    location: CompilerMessageSourceLocation?,
  ) {
    return if (filter(severity)) {
      delegate.report(severity, message, location)
    } else {
      // Do nothing
    }
  }
}
