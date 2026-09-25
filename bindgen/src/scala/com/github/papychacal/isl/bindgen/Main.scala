package com.github.papychacal.isl.bindgen

import java.nio.file.Paths

object Main:
  def main(args: Array[String]): Unit =
    if args.length < 3 then
      System.err.println("usage: bindgen <project-root> <output-directory> <include-directory>...")
      System.exit(2)

    val root = Paths.get(args(0)).toAbsolutePath.normalize
    val output = Paths.get(args(1)).toAbsolutePath.normalize
    val includes = args.drop(2).map(Paths.get(_)).toSeq
    val extractor = ClangExtractor(includes, root)
    System.err.println("extracting ISL headers")
    val extracted = extractor.extractHeader(root.resolve("isl/all.h"))
    System.err.println("extracting implementation documentation")
    val documentation = SourceDocumentation.enrich(extracted, root, root.resolve("isl"), includes)
    System.err.println("rendering Scala sources")
    val rendered = ScalaRenderer.render(documentation.model)
    val parseDiagnostics = extracted.diagnostics.filter(_.severity >= 3).map(d => s"clang: ${d.message}")
    val result = rendered.copy(diagnostics =
      (rendered.diagnostics ++ documentation.ambiguities ++ parseDiagnostics ++
        Vector(s"Parsed ${documentation.parsedSources} implementation translation units for documentation."))
        .distinct.sorted
    )
    ScalaRenderer.write(result, output)
    System.err.println(
      s"generated ${documentation.model.functions.size} declarations, ${documentation.model.enums.size} enums; " +
        s"${result.diagnostics.count(_.startsWith("unsupported"))} unsupported mappings"
    )
