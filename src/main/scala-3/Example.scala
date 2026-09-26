package com.github.papychacal.isl

object Example:
  def main(args: Array[String]): Unit =
    assert(IslBool.Error.nativeValue == -1)
    assert(IslStat.Error.nativeValue == -1)
    assert(IslSize.Error.isError)
    assert(IslSize.Error.toLongOption.isEmpty)
    val ctx = Ctx()
    val basicSet = BasicSet(ctx, "{ [i, j] : 0 <= i <= 2 and 0 <= j <= 2 }")
    assert(basicSet.isEmpty() == IslBool.False)
    val set = basicSet.toSet()
    var points = 0
    val status = set.foreachPoint((_, _) => {
      points += 1
      IslStat.Ok
    }, null)
    assert(status == IslStat.Ok, s"foreach failed with $status after $points points")
    assert(points == 9, s"expected 9 points, visited $points")

    val parseError = try
      BasicSet(ctx, "this is not an ISL set")
      throw AssertionError("invalid input unexpectedly parsed")
    catch case error: IslError => error
    assert(parseError.kind == IslErrorKind.Invalid)
    assert(parseError.nativeMessage.nonEmpty)
    assert(parseError.nativeSymbol == "isl_basic_set_read_from_str")
