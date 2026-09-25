package com.github.papychacal.isl.unsafe

import java.nio.file.{Files, StandardCopyOption}
import jnr.ffi.LibraryLoader

private[unsafe] object NativeLibrary:
  def load[A](interface: Class[A]): A =
    LibraryLoader.create(interface).load(NativeLibraryResource.path)

private object NativeLibraryResource:
  lazy val path: String =
    val stream = Option(getClass.getResourceAsStream("/libisl.so"))
      .getOrElse(throw UnsatisfiedLinkError("bundled native library /libisl.so was not found"))
    val file = Files.createTempFile("scalaisl-", "-libisl.so")
    try Files.copy(stream, file, StandardCopyOption.REPLACE_EXISTING)
    finally stream.close()
    file.toFile.deleteOnExit()
    file.toAbsolutePath.toString
