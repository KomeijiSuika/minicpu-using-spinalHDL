package minicpu.validation

object ValidationRegistry {
  private val suites = Seq(
    GoldenRV32I.content,
    GoldenRV32M.content
  ).map(suite => suite.key -> suite).toMap

  def suiteOrThrow(key: String): ValidationSuite = {
    suites.getOrElse(
      key,
      throw new IllegalArgumentException(
        s"Unknown validation suite '$key'. Available: ${suites.keys.toSeq.sorted.mkString(", ")}"
      )
    )
  }
}
