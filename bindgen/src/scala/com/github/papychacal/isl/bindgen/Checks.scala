package com.github.papychacal.isl.bindgen

import java.nio.file.{Files, Paths}

object Checks:
  def main(args: Array[String]): Unit =
    val root = Paths.get(args(0)).toAbsolutePath.normalize
    val fixtures = root.resolve("bindgen/fixtures")
    val model = ClangExtractor(Seq(fixtures), root).extractHeader(fixtures.resolve("api.h"))

    assert(model.enums.exists(e => e.name == "isl_fixture_mode" && e.values.map(_.value).contains(2)))
    val constructor = model.functions.find(_.name == "isl_fixture_read_from_str").get
    assert(constructor.constructor)
    assert(constructor.resultOwnership.contains(Ownership.Give))
    assert(constructor.parameters.head.ownership.contains(Ownership.Keep))
    assert(model.typedefs.exists(t => t.name == "isl_fixture_callback" && t.underlying.isCallback))

    val enriched = SourceDocumentation.enrich(model, root, fixtures, Seq(fixtures)).model
    assert(enriched.functions.find(_.name == "isl_fixture_coalesce").flatMap(_.comment)
      .exists(_.contains("comment deliberately lives only")))

    val rendered = ScalaRenderer.render(enriched)
    val combined = rendered.files.map(_.contents).mkString("\n")
    assert(combined.contains("Construct a fixture from a textual description."))
    assert(combined.contains("trait Callback"))
    assert(combined.contains("final class Fixture"))
    val emptyClass = "final class Empty private[isl] (private[isl] val handle: NativeHandle)"
    assert(combined.contains(emptyClass))
    assert(!combined.contains(emptyClass + ":"))
    assert(combined.contains("CallbackRegistration[Fixture]"))
    assert(combined.contains("NativeCall.requiredPointer"))
    assert(combined.contains("isl_fixture_get_ctx(handle.pointer)"))
    assert(combined.contains("def apply(text: String)(using ctx: Ctx): Fixture"))
    assert(!combined.contains("handle.context"))
    assert(!combined.contains("Option(native.ISLLibrary"))
    assert(combined.contains("NativeLibrary.load(classOf[ISLLibrary"))
    assert(!combined.contains("NativeLibraryResource"))
    assert(!combined.contains("cleaner.register"))
    assert(!combined.contains("enum IslBool"))
    assert(!combined.contains("opaque type IslSize"))
    assert(!combined.contains("AutoCloseable"))
    assert(!combined.contains("def close(): Unit"))
    assert(!combined.contains("Result ownership:"))
    assert(!combined.contains("Parameter `fixture` ownership:"))
    assert(!combined.contains("Implementation:"))
    assert(!combined.contains("Declaration:"))
    assert(!rendered.diagnostics.exists(_.startsWith("unsupported raw declaration")))

    val formatted = ScaladocFormatter.format(Seq("first paragraph\n\ncode */ fragment"))
    assert(formatted.contains("first paragraph"))
    assert(formatted.contains("*&#47;"))
    println("bindgen fixture checks passed")
