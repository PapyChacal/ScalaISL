# ScalaISL

Scala bindings to the [Integer Set Library (ISL)](https://libisl.sourceforge.io/)

## **Disclaimer**

This project is in an **experimental state**, is **not intended for serious or production use at this stage**, and **might never be**. It was developed to support my own research and workflow. There are **no guarantees** of stability, maintenance, completeness, or particular direction.

## What is there

- Scala types for core ISL constructs: `isl_set`, `isl_map`, etc.
- Simple method syntax to call most functions.
- Explicit, non-throwing mappings for `isl_bool`, `isl_stat`, `isl_size`, and nullable object results.
- Ownership-aware wrappers with best-effort `Cleaner` release.
- Generated Scaladoc recovered from ISL headers and implementation files.
- A Scala 3/libclang binding generator and a [`jnr-ffi`](https://github.com/jnr/jnr-ffi) JVM runtime.

## Current limitations

- The public mapping report still identifies declarations that need bespoke
  wrappers, such as by-value `isl_maybe_*` structs and consuming types without
  a native copy operation.
- Distribution beyond linux-x86 has not been validated.

## Quick Example

See [`src/main/scala-3/Example.scala`](src/main/scala-3/Example.scala).

The public API preserves ISL failures as values. Constructors return `Option`,
predicates return `IslBool`, status operations return `IslStat`, and sizes use
the opaque `IslSize` type.

## How to

### Compile

The build downloads the official ISL 0.28 release archive from
[`libisl.sourceforge.io`](https://libisl.sourceforge.io/isl-0.28.tar.gz) and
builds it locally. The Scala/libclang generator parses this downloaded source
tree directly; the source tree is never patched or modified.
The currently published artifact bundles a Linux x86-64 native library using
ISL's `imath-32` backend, so consumers do not need a system ISL or GMP
installation.

```bash
./mill compile
```

### Run example code

```bash
./mill runMain com.github.papychacal.isl.Example
```

### Check the generator fixtures

```bash
./mill bindgen.check
```

### Build Scaladoc

```bash
./mill docJar
```

Generated Scala sources live only in Mill task output. They are regenerated
from the downloaded ISL sources and are not committed.

### Publish locally
to build and use as a dependency locally, e.g. try it out on non-linux and/or non-x86.

```bash
./mill publishLocal
```
