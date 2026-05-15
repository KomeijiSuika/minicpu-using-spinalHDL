package minicpu.validation

import minicpu.{CpuConfig}
import minicpu.core.CpuTop
import spinal.core.sim._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._

object ValidationHarness {
  private val Mask32 = BigInt("ffffffff", 16)

  private def freshWorkspace(prefix: String): String = {
    val root = Paths.get("simWorkspace")
    Files.createDirectories(root)
    Files.createTempDirectory(root, prefix + "_").toAbsolutePath.toString
  }

  private def simConfig(prefix: String): SpinalSimConfig = {
    SimConfig
      .workspacePath(freshWorkspace(prefix))
      .withVerilator
      .addSimulatorFlag("-CFLAGS")
      .addSimulatorFlag("-std=c++17")
      .addSimulatorFlag("-LDFLAGS")
      .addSimulatorFlag("-std=c++17")
  }

  private def zeroState(dut: CpuTop): Unit = {
    for (i <- 0 until 1024) setBigInt(dut.instrMem.instrMem, i.toLong, BigInt(0))
    for (i <- 0 until 1024) setBigInt(dut.dataMem.dataMem, i.toLong, BigInt(0))
    for (i <- 0 until 32) setBigInt(dut.regFile.regFile, i.toLong, BigInt(0))
  }

  private def loadProgram(dut: CpuTop, program: Seq[Long]): Unit = {
    val instrMem = dut.instrMem.instrMem
    program.zipWithIndex.foreach { case (instr, idx) =>
      instrMem.setBigInt(idx, BigInt(instr & 0xffffffffL))
    }
  }

  private def formatHex32(value: BigInt): String = {
    f"0x${(value & Mask32).toLong}%08x"
  }

  private def readRegister(dut: CpuTop, index: Int): BigInt = {
    dut.io.dbg_regAddr #= index
    dut.clockDomain.waitSampling()
    BigInt(dut.io.dbg_regData.toLong) & Mask32
  }

  private def readMemWord(dut: CpuTop, wordIndex: Int): BigInt = {
    dut.io.dbg_memAddr #= (wordIndex * 4)
    dut.clockDomain.waitSampling()
    BigInt(dut.io.dbg_memData.toLong) & Mask32
  }

  private def runCase(prefix: String, config: CpuConfig, testCase: ValidationCase): ValidationCaseResult = {
    var result: ValidationCaseResult = null
    val compiled = simConfig(prefix).compile(new CpuTop(config))

    compiled.doSim { dut: CpuTop =>
      dut.io.dbg_regAddr #= 0
      dut.io.dbg_memAddr #= 0
      dut.io.dbg_memWriteEnable #= false
      dut.io.dbg_memWriteData #= 0

      dut.clockDomain.forkStimulus(10)
      dut.clockDomain.assertReset()
      zeroState(dut)
      loadProgram(dut, testCase.program)

      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()
      dut.clockDomain.waitSampling(2)

      for (_ <- 0 until testCase.cycleLimit) {
        dut.clockDomain.waitSampling()
      }

      val regChecks = testCase.expectedRegs.toSeq.sortBy(_._1).map { case (index, expected) =>
        ValidationCheck(
          label = s"reg x$index",
          expected = expected & Mask32,
          actual = readRegister(dut, index)
        )
      }

      val memChecks = testCase.expectedMemWords.toSeq.sortBy(_._1).map { case (index, expected) =>
        ValidationCheck(
          label = f"mem[0x${index * 4}%08x]",
          expected = expected & Mask32,
          actual = readMemWord(dut, index)
        )
      }

      result = ValidationCaseResult(
        name = testCase.name,
        cycles = testCase.cycleLimit,
        checks = regChecks ++ memChecks
      )
    }

    result
  }

  private def writeReport(suite: ValidationSuite, results: Seq[ValidationCaseResult], outPath: Path): Unit = {
    val passed = results.count(_.passed)
    val failed = results.length - passed

    val lines = Vector.newBuilder[String]
    lines += s"SUITE: ${suite.key}"
    lines += s"TITLE: ${suite.title}"
    lines += s"TOTAL_CASES: ${results.length}"
    lines += s"PASSED_CASES: $passed"
    lines += s"FAILED_CASES: $failed"
    lines += s"RESULT: ${if (failed == 0) "PASS" else "FAIL"}"
    lines += ""

    results.foreach { result =>
      lines += s"CASE: ${result.name}"
      lines += s"CYCLES: ${result.cycles}"
      lines += s"RESULT: ${if (result.passed) "PASS" else "FAIL"}"
      result.checks.foreach { check =>
        val status = if (check.passed) "PASS" else "FAIL"
        lines += f"  [$status] ${check.label}%-16s expected=${formatHex32(check.expected)} actual=${formatHex32(check.actual)}"
      }
      lines += ""
    }

    Files.createDirectories(outPath.getParent)
    Files.write(outPath, lines.result().asJava, StandardCharsets.UTF_8)
  }

  def runSuite(suite: ValidationSuite, config: CpuConfig = CpuConfig()): Path = {
    val results = suite.cases.zipWithIndex.map { case (testCase, index) =>
      runCase(s"${suite.key.toLowerCase}_$index", config, testCase)
    }
    val outPath = Paths.get("build", s"${suite.key}.txt")

    writeReport(suite, results, outPath)

    println(s"[validation] suite=${suite.key} out=$outPath")
    results.foreach { result =>
      println(s"[validation] case=${result.name} result=${if (result.passed) "PASS" else "FAIL"} cycles=${result.cycles}")
    }

    if (results.exists(!_.passed)) {
      throw new AssertionError(s"${suite.key} failed. See $outPath for details.")
    }

    outPath
  }
}
