package com.github.papychacal.isl.unsafe

import java.nio.file.{Files, Path, StandardCopyOption}
import jnr.ffi.LibraryLoader

private[unsafe] object NativeLibrary:
  private val libraryName = System.mapLibraryName("isl")
  private val resource = s"/native/$libraryName"

  private lazy val extractedDirectory: Path =
    val input = Option(getClass.getResourceAsStream(resource)).getOrElse {
      throw UnsatisfiedLinkError(s"ScalaISL native library resource is missing: $resource")
    }
    val directory = Files.createTempDirectory("scalaisl-")
    val library = directory.resolve(libraryName)
    try Files.copy(input, library, StandardCopyOption.REPLACE_EXISTING)
    finally input.close()
    directory.toFile.deleteOnExit()
    library.toFile.deleteOnExit()
    directory

  def load[A](interface: Class[A]): A =
    LibraryLoader
      .create(interface)
      .search(extractedDirectory.toAbsolutePath.toString)
      .failImmediately()
      .load("isl")
