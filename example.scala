

import jnr.ffi.*
import jnr.ffi.types.*
import jnr.ffi.byref.*
import jnr.ffi.annotations.Delegate

given[A]: Conversion[A, Pointer] => Conversion

package scalaisl {
  private[scalaisl] trait ISLLib:
    def isl_ctx_alloc(): Pointer

  private[scalaisl] val lib = LibraryLoader.create(classOf[ISLLib])
    .load("isl")

  object Ctx:
    private[scalaisl] given Conversion[Ctx, Pointer] = _.p
    private[scalaisl] given Conversion[Pointer, Ctx] = Ctx(_)
  class Ctx private[scalaisl] (private[scalaisl] var p : Pointer)  {
    def this() = this(lib.isl_ctx_alloc())
  }
}
import scalaisl.{Ctx, ISLLib, lib}
object ISL:
  
  def main(args: Array[String]): Unit =
    given ctx: Ctx = Ctx()
    println(ctx)
    println(ctx.p)
    lib.isl_ctx_alloc()
    println(ClassTag[ISLLib])
