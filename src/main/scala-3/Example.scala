package com.github.papychacal.isl

object Example:
  def main(args: Array[String]): Unit =
    assert(IslBool.Error.nativeValue == -1)
    assert(IslStat.Error.nativeValue == -1)
    assert(IslSize.Error.isError)
    assert(IslSize.Error.toLongOption.isEmpty)
    val ctx = Ctx().getOrElse(throw IllegalStateException("unable to allocate an ISL context"))
    try
      val basicSet = BasicSet(ctx, "{ [i, j] : 0 <= i <= 2 and 0 <= j <= 2 }")
        .getOrElse(throw IllegalStateException("unable to parse the example set"))
      try
        assert(basicSet.isEmpty() == IslBool.False)
        val set = basicSet.toSet().getOrElse(throw IllegalStateException("unable to convert the basic set"))
        try
          var points = 0
          val status = set.foreachPoint((_, _) => {
            points += 1
            IslStat.Ok
          }, null)
          assert(status == IslStat.Ok, s"foreach failed with $status after $points points")
          assert(points == 9, s"expected 9 points, visited $points")
        finally set.close()
      finally basicSet.close()
    finally ctx.close()
