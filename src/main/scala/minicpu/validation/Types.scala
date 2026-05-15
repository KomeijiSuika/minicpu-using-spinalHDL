package minicpu.validation

case class ValidationCase(
    name: String,
    program: Seq[Long],
    cycleLimit: Int,
    expectedRegs: Map[Int, BigInt] = Map.empty,
    expectedMemWords: Map[Int, BigInt] = Map.empty
)

case class ValidationSuite(
    key: String,
    title: String,
    cases: Seq[ValidationCase]
)

case class ValidationCheck(
    label: String,
    expected: BigInt,
    actual: BigInt
) {
  def passed: Boolean = expected == actual
}

case class ValidationCaseResult(
    name: String,
    cycles: Int,
    checks: Seq[ValidationCheck]
) {
  def passed: Boolean = checks.forall(_.passed)
}
