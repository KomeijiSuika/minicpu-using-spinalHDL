package minicpu.validation

object ValidationMain {
  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Usage: runMain minicpu.validation.ValidationMain <TestRV32I|TestRV32M>")

    val key = args(0)
    val suite = ValidationRegistry.suiteOrThrow(key)
    val outPath = ValidationHarness.runSuite(suite)

    println(s"[validation] ${suite.key} PASS")
    println(s"[validation] report: $outPath")
  }
}
