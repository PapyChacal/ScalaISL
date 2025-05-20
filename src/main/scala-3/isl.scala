import com.github.papychacal.isl.*

// given cvtptr: [C] => Conversion[C, Pointer] => Conversion[AbstractReference[C], AbstractReference[Pointer]] = (ref:AbstractReference[C]) => new AbstractReference(ref.getValue().p)

object ISL:
  
  def main(args: Array[String]): Unit =
    given ctx: Ctx = Ctx()
    val str = "{ [i, j] : 0 <= i <= 10 and 0 <= j <= 10 }"
    val basicSet = BasicSet(str)
    println(basicSet.samplePoint())
