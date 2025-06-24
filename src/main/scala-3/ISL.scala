import com.github.papychacal.isl.*

object Example:
  
  def main(args: Array[String]): Unit =
    given ctx: Ctx = Ctx()
    val str = "{ [i, j] : 0 <= i <= 10 and 0 <= j <= 10 }"
    val basicSet = BasicSet(str)
    println(basicSet.samplePoint())
