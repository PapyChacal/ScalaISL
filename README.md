# ScalaISL

Scala bindings to the [Integer Set Library (ISL)](https://libisl.sourceforge.io/)

## **Disclaimer**

This project is in an **experimental state**, is **not intended for serious or production use at this stage**, and **might never be**. It was developed to support my own research and workflow. There are **no guarantees** of stability, maintenance, completeness, or particular direction.

## What is there

- Scala types for core ISL constructs: `isl_set`, `isl_map`, etc.
- Simple method syntax to call most functions.
- Basic contextual argument usage for `isl_context`.
- Built using ISL's interface extractor and [`jnr-ffi`](https://github.com/jnr/jnr-ffi)

## What is not

- Documentation
- Tests
- Distribution beyond linux-x86

## Quick Example

https://github.com/PapyChacal/ScalaISL/blob/main/src/main/scala-3/isl.scala

## How to

### Compile

The build downloads the official ISL 0.28 release archive from
[`libisl.sourceforge.io`](https://libisl.sourceforge.io/isl-0.28.tar.gz),
verifies its SHA-256 checksum, and builds it locally. The Scala-specific
interface generator remains in this repository under `isl-interface-scala/`.
The currently published artifact bundles a Linux x86-64 native library using
ISL's `imath-32` backend, so consumers do not need a system ISL or GMP
installation.

```bash
./mill compile
```

### Run example code
in `src/main/scala-3/isl.scala`

```bash
./mill run
```

### Publish locally
to build and use as a dependency locally, e.g. try it out on non-linux and/or non-x86.

```bash
./mill publishLocal
```
