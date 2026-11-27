package com.tschuchort.compiletesting

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import okio.Buffer
import org.assertj.core.api.Assertions
import org.junit.Test

class StreamUtilTests {

  @Test
  fun `TeeOutputStream prints to all streams`() {
    val buf1 = Buffer()
    val buf2 = Buffer()

    val s = "test test \ntest\n"

    PrintStream(TeeOutputStream(PrintStream(buf1.outputStream()), buf2.outputStream())).print(s)

    Assertions.assertThat(buf1.readUtf8()).isEqualTo(s)
    Assertions.assertThat(buf2.readUtf8()).isEqualTo(s)
  }

  @Test
  fun `TeeOutPutStream flushes all streams`() {
    val str1 = RecordingOutputStream()
    val str2 = RecordingOutputStream()

    TeeOutputStream(str1, str2).flush()

    assertEquals(1, str1.flushCount)
    assertEquals(1, str2.flushCount)
  }

  @Test
  fun `TeeOutPutStream closes all streams`() {
    val str1 = RecordingOutputStream()
    val str2 = RecordingOutputStream()

    TeeOutputStream(str1, str2).close()

    assertEquals(1, str1.closeCount)
    assertEquals(1, str2.closeCount)
  }

  private class RecordingOutputStream : ByteArrayOutputStream() {
    var flushCount = 0
      private set

    var closeCount = 0
      private set

    override fun flush() {
      flushCount++
    }

    override fun close() {
      closeCount++
    }
  }
}
