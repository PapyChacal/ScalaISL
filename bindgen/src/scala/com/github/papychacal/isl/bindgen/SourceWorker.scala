package com.github.papychacal.isl.bindgen

import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.Base64

/** Isolates each production translation unit because libclang cannot recover from a native parser crash. */
object SourceWorker:
  def main(args: Array[String]): Unit =
    val root = Paths.get(args(0)).toAbsolutePath.normalize
    val source = Paths.get(args(1)).toAbsolutePath.normalize
    val includes = args.drop(2).map(Paths.get(_)).toSeq
    val model = ClangExtractor(includes, root).extractSource(source)
    // Main-file-only extraction means these cursors are production definitions;
    // libclang reports them as declarations when function bodies are skipped.
    model.functions.foreach { function =>
      val fields = Seq(
        function.signature,
        function.comment.getOrElse(""),
        function.location.file.toString,
        function.location.line.toString,
        function.location.column.toString
      ).map(encode)
      println(fields.mkString("\t"))
    }

  private def encode(value: String): String =
    Base64.getUrlEncoder.withoutPadding.encodeToString(value.getBytes(StandardCharsets.UTF_8))
