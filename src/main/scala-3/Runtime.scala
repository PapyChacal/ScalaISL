package com.github.papychacal.isl

import java.lang.ref.Cleaner
import jnr.ffi.Pointer

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

private final class NativeState(pointer: Pointer, release: Pointer => Unit) extends Runnable:
  override def run(): Unit = release(pointer)

private[isl] final class NativeHandle private (val pointer: Pointer)

private[isl] object NativeHandle:
  private val cleaner = Cleaner.create()

  def owned(pointer: Pointer, release: Pointer => Unit): NativeHandle =
    val handle = NativeHandle(pointer)
    cleaner.register(handle, NativeState(pointer, release))
    handle

  def borrowed(pointer: Pointer): NativeHandle = NativeHandle(pointer)

final class CallbackRegistration[+A] private[isl] (
    val value: A,
    private val retained: List[AnyRef]
)

private[isl] object CallbackRegistration:
  def apply[A](value: A, retained: List[AnyRef]): CallbackRegistration[A] =
    new CallbackRegistration(value, retained)
