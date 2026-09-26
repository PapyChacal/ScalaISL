package com.github.papychacal.isl.unsafe

import java.nio.file.{Files, Path, StandardCopyOption}
import jnr.ffi.Pointer
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

private[isl] trait RuntimeLibrary:
  def isl_ctx_last_error(ctx: Pointer): Int
  def isl_ctx_last_error_msg(ctx: Pointer): String
  def isl_ctx_last_error_file(ctx: Pointer): String
  def isl_ctx_last_error_line(ctx: Pointer): Int
  def isl_ctx_reset_error(ctx: Pointer): Unit
  def isl_options_set_on_error(ctx: Pointer, value: Int): Int

private[isl] object RuntimeLibrary:
  lazy val instance: RuntimeLibrary = NativeLibrary.load(classOf[RuntimeLibrary])
