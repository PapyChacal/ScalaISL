# Scala ISL binding generator

The generator has three deliberately separate pieces:

1. `ClangExtractor` reads `isl/all.h` through libclang, including comments,
   callback types, source locations, and ISL ownership annotations.
2. `IslModel` is the semantic Scala representation used by later stages.
3. `ScalaRenderer` emits the low-level JNR declarations and the public Scala
   API. `ScaladocFormatter` only formats ordinary comments embedded in those
   generated definitions; Mill remains responsible for Scaladoc.

For declarations without useful header prose, `SourceDocumentation` locates
commented production definitions and matches them by canonical C signature.
Each implementation translation unit is parsed in a short-lived worker JVM so
a native libclang failure can be reported without taking down the build.

`generation-report.txt` is emitted beside the generated `.scala` files. Every
header declaration is either present in the raw layer or reported there as
unsupported. Public-layer omissions and ambiguous source-comment matches are
also explicit. The report is a diagnostic build artifact, not a checked-in
drift file.

JavaCPP/libclang is a build-only dependency. Generated runtime code depends
only on JNR-FFI. The generator currently runs interpreted (`-Xint`) because the
LLVM 21 JavaCPP bindings have shown JIT-dependent native-memory corruption on
JDK 25 while repeatedly traversing large ISL translation units.

## Inventory baseline

The current ISL headers contain 2,851 `isl_*` function declarations. The new
raw layer maps 2,849 of them and reports the two by-value `isl_maybe_*`
structures it cannot yet represent. The legacy renderer explicitly skipped
five symbols in `isl/interface/scala.cc`; three of those (`isl_ctx_parse_options`,
`isl_mat_left_hermite`, and `isl_args_parse`) are present in the new raw layer.
The legacy source remains in the vendored ISL tree for reference, but Mill no
longer compiles or invokes it.

## Owned and borrowed wrappers

JNR's `Pointer` does not encode ISL's ownership contract. ISL does: a
`__isl_give` result transfers a reference to the caller, while a `__isl_keep`
result is only a view owned elsewhere. Treating both the same either leaks
native objects or eventually frees an object that ISL still owns.

The public wrapper classes therefore share a small internal `NativeHandle`:

- an **owned** handle invokes the corresponding `isl_*_free` function exactly
  once when closed, with a `Cleaner` as a fallback;
- a **borrowed** handle never frees its pointer;
- a `__isl_take` argument is copied when ISL provides a copy function, so a
  Scala method call does not unexpectedly consume its receiver or argument.

This distinction is intentionally internal—users see the same Scala wrapper
type. It is primarily resource and use-after-free protection; it is not a full
static borrow checker. In particular, a borrowed result must not outlive the
native object from which it was obtained. Short-lived examples can appear to
work without this layer because the process exits before leaks matter and many
ISL objects are reference-counted, but long-running JVM applications cannot
rely on that behavior.
