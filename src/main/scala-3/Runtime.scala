package com.github.papychacal.isl

import java.lang.ref.Cleaner
import jnr.ffi.Pointer
import com.github.papychacal.isl.unsafe.RuntimeLibrary

enum IslBool(val nativeValue: Int):
  case Error extends IslBool(-1)
  case False extends IslBool(0)
  case True extends IslBool(1)

object IslBool:
  def fromNative(value: Int): IslBool = value match
    case -1 => Error
    case 0 => False
    case 1 => True
    case other => throw IllegalArgumentException(s"invalid isl_bool ABI value: $other")

enum IslStat(val nativeValue: Int):
  case Error extends IslStat(-1)
  case Ok extends IslStat(0)

object IslStat:
  def fromNative(value: Int): IslStat = value match
    case -1 => Error
    case 0 => Ok
    case other => throw IllegalArgumentException(s"invalid isl_stat ABI value: $other")

opaque type IslSize = Long

object IslSize:
  val Error: IslSize = -1L
  def apply(raw: Long): IslSize = raw
  extension (size: IslSize)
    def raw: Long = size
    def isError: Boolean = size == Error
    def toLongOption: Option[Long] = Option.unless(isError)(size)

enum IslErrorKind:
  case Abort, Allocation, Unknown, Internal, Invalid, Quota, Unsupported
  case MissingNativeDiagnostic

/** An error reported by ISL while evaluating a native call. */
final class IslError private[isl] (
    val kind: IslErrorKind,
    val nativeMessage: Option[String],
    val nativeFile: Option[String],
    val nativeLine: Option[Int],
    val nativeSymbol: String
) extends RuntimeException(IslError.message(kind, nativeMessage, nativeFile, nativeLine, nativeSymbol))

private object IslError:
  private def message(
      kind: IslErrorKind,
      nativeMessage: Option[String],
      nativeFile: Option[String],
      nativeLine: Option[Int],
      nativeSymbol: String
  ): String =
    val detail = nativeMessage.getOrElse("ISL returned null without recording an error")
    val location = nativeFile.map(file => s" at $file${nativeLine.fold("")(line => s":$line")}").getOrElse("")
    s"$nativeSymbol failed ($kind): $detail$location"

private[isl] object NativeCall:
  private val ErrorNone = 0
  private val OnErrorContinue = 1

  def allocateContext(nativeSymbol: String)(call: => Pointer): Pointer =
    val pointer = call
    if pointer == null then
      throw new IslError(
        IslErrorKind.MissingNativeDiagnostic,
        Some("ISL could not allocate a context"),
        None,
        None,
        nativeSymbol
      )
    val status = RuntimeLibrary.instance.isl_options_set_on_error(pointer, OnErrorContinue)
    if status < 0 then throw takeError(pointer, "isl_options_set_on_error")
    RuntimeLibrary.instance.isl_ctx_reset_error(pointer)
    pointer

  def contextPointer(nativeSymbol: String)(call: => Pointer): Pointer =
    val pointer = call
    if pointer != null then pointer
    else
      throw new IslError(
        IslErrorKind.MissingNativeDiagnostic,
        Some("ISL object has no context"),
        None,
        None,
        nativeSymbol
      )

  def requiredPointer(context: Pointer, nativeSymbol: String)(call: => Pointer): Pointer =
    prepare(context)
    val pointer = call
    if pointer != null then pointer
    else throw errorOrMissingDiagnostic(context, nativeSymbol)

  def optionalPointer(context: Pointer, nativeSymbol: String)(call: => Pointer): Option[Pointer] =
    prepare(context)
    val pointer = call
    if pointer != null then Some(pointer)
    else if RuntimeLibrary.instance.isl_ctx_last_error(context) == ErrorNone then None
    else throw takeError(context, nativeSymbol)

  private def prepare(context: Pointer): Unit =
    if context == null then throw IllegalStateException("native object has no ISL context")
    RuntimeLibrary.instance.isl_ctx_reset_error(context)

  private def errorOrMissingDiagnostic(context: Pointer, nativeSymbol: String): IslError =
    if RuntimeLibrary.instance.isl_ctx_last_error(context) == ErrorNone then
      new IslError(IslErrorKind.MissingNativeDiagnostic, None, None, None, nativeSymbol)
    else takeError(context, nativeSymbol)

  private def takeError(context: Pointer, nativeSymbol: String): IslError =
    val library = RuntimeLibrary.instance
    val code = library.isl_ctx_last_error(context)
    val message = Option(library.isl_ctx_last_error_msg(context))
    val file = Option(library.isl_ctx_last_error_file(context))
    val line = library.isl_ctx_last_error_line(context)
    library.isl_ctx_reset_error(context)
    new IslError(
      errorKind(code),
      message,
      file,
      Option.when(line >= 0)(line),
      nativeSymbol
    )

  private def errorKind(code: Int): IslErrorKind = code match
    case 1 => IslErrorKind.Abort
    case 2 => IslErrorKind.Allocation
    case 3 => IslErrorKind.Unknown
    case 4 => IslErrorKind.Internal
    case 5 => IslErrorKind.Invalid
    case 6 => IslErrorKind.Quota
    case 7 => IslErrorKind.Unsupported
    case _ => IslErrorKind.MissingNativeDiagnostic

private[isl] final class NativeHandle private (val pointer: Pointer)

private[isl] object NativeHandle:
  private val cleaner = Cleaner.create()

  def owned(pointer: Pointer, release: Pointer => Unit): NativeHandle =
    val handle = NativeHandle(pointer)
    cleaner.register(handle, () => release(pointer))
    handle

  def borrowed(pointer: Pointer): NativeHandle = NativeHandle(pointer)

final class CallbackRegistration[+A] private[isl] (
    val value: A,
    private val retained: List[AnyRef]
)

private[isl] object CallbackRegistration:
  def apply[A](value: A, retained: List[AnyRef]): CallbackRegistration[A] =
    new CallbackRegistration(value, retained)
