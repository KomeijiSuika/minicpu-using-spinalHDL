package minicpu

import minicpu.core.CpuTop
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.exceptions.TestCanceledException
import spinal.core.sim._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

object MemhSupport {
  def parseMemhWords(memhPath: Path): Seq[(Long, BigInt)] = {
    val out = ArrayBuffer.empty[(Long, BigInt)]
    var wordIndex = 0L

    val lines = Files.readAllLines(memhPath, StandardCharsets.UTF_8).asScala
    for (raw <- lines) {
      val cut = raw.indexOf("//")
      val noComment = (if (cut >= 0) raw.substring(0, cut) else raw).trim
      if (noComment.nonEmpty) {
        if (noComment.startsWith("@")) {
          val addrHex = noComment.drop(1).trim
          wordIndex = BigInt(addrHex, 16).toLong
        } else {
          val hex = noComment.replace("_", "")
          val value = BigInt(hex, 16) & BigInt("ffffffff", 16)
          out += wordIndex -> value
          wordIndex += 1
        }
      }
    }
    out.toSeq
  }

  def hex32(value: BigInt): String = {
    val masked = value & BigInt("ffffffff", 16)
    val s = masked.toString(16)
    "0" * (8 - s.length) + s
  }
}

class PipelinedRv32iProgramTest extends AnyFunSuite {
  private def requireFileOrCancel(path: Path, hint: String): Unit = {
    if (!Files.exists(path)) {
      throw new TestCanceledException(
        s"Missing file: $path\n$hint",
        0
      )
    }
  }

  private def firstDiffIndex(actual: Seq[String], expected: Seq[String]): Int = {
    val n = Math.min(actual.length, expected.length)
    var i = 0
    while (i < n) {
      if (actual(i) != expected(i)) return i
      i += 1
    }
    if (actual.length != expected.length) n else -1
  }

  private def writeAndVerifyAgainstExpected(
      kind: String,
      programName: String,
      lines: Seq[String],
      outDir: Path,
      expectedDir: Path
  ): Unit = {
    val outPath = outDir.resolve(s"${kind}_${programName}.txt")
    Files.write(outPath, lines.asJava, StandardCharsets.UTF_8)

    val expectedPath = expectedDir.resolve(s"${kind}_${programName}.txt")
    requireFileOrCancel(
      expectedPath,
      hint =
        s"Missing expected file: $expectedPath\n" +
          s"Use local-rv32i/tools/generate_expected.py to generate expected baselines first."
    )

    val expected = Files.readAllLines(expectedPath, StandardCharsets.UTF_8).asScala.toSeq
    val diff = firstDiffIndex(lines, expected)
    assert(
      diff == -1,
      s"Mismatch in $kind for program '$programName' at line ${diff + 1}.\n" +
        s"actual  : ${if (diff >= 0 && diff < lines.length) lines(diff) else "<no line>"}\n" +
        s"expected: ${if (diff >= 0 && diff < expected.length) expected(diff) else "<no line>"}\n" +
        s"actual file  : $outPath\nexpected file: $expectedPath"
    )
  }

  private def runProgram(
      memh: Path,
      maxCycles: Int,
      expectedDir: Path
  ): Unit = {
    requireFileOrCancel(
      memh,
      hint = "Generate .memh via local-rv32i/tools/assemble_memh.py first."
    )

    val programName = memh.getFileName.toString.replaceAll("\\.memh$", "")
    val outDir = Paths.get("sim_out")
    Files.createDirectories(outDir)

    SimConfig.withWave.compile(new CpuTop(CpuConfig())).doSim { dut =>
      dut.clockDomain.forkStimulus(period = 10)

      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()

      for (i <- 0 until 1024) setBigInt(dut.instrMem.instrMem, i.toLong, BigInt(0))
      for (i <- 0 until 1024) setBigInt(dut.dataMem.dataMem, i.toLong, BigInt(0))
      for (i <- 0 until 32) setBigInt(dut.regFile.regFile, i.toLong, BigInt(0))

      val image = MemhSupport.parseMemhWords(memh)
      image.foreach { case (idx, word) =>
        if (idx >= 0 && idx < 1024) setBigInt(dut.instrMem.instrMem, idx, word)
      }

      for (_ <- 0 until maxCycles) {
        dut.clockDomain.waitSampling()
      }

      val regLines = (0 until 32).map { i =>
        val v = getBigInt(dut.regFile.regFile, i.toLong)
        f"x$i%02d = 0x${MemhSupport.hex32(v)}"
      }
      writeAndVerifyAgainstExpected(
        kind = "regfile",
        programName = programName,
        lines = regLines,
        outDir = outDir,
        expectedDir = expectedDir
      )

      val memLines = (0 until 1024).map { i =>
        val addr = i * 4
        val v = getBigInt(dut.dataMem.dataMem, i.toLong)
        f"0x$addr%08x : 0x${MemhSupport.hex32(v)}"
      }
      writeAndVerifyAgainstExpected(
        kind = "datamem",
        programName = programName,
        lines = memLines,
        outDir = outDir,
        expectedDir = expectedDir
      )

      val regPath = outDir.resolve(s"regfile_$programName.txt")
      val memPath = outDir.resolve(s"datamem_$programName.txt")
      println(s"[PipelinedRv32iProgramTest] program=$memh cycles=$maxCycles")
      println(s"[PipelinedRv32iProgramTest] reg dump : $regPath")
      println(s"[PipelinedRv32iProgramTest] mem dump : $memPath")
      println(s"[PipelinedRv32iProgramTest] expected dir: $expectedDir")
    }
  }

  test("Run local RV32I program (.memh) and compare outputs") {
    val defaultMemh = "local-rv32i/asm/itypes.memh"
    val memhPath =
      sys.props.get("rv32i.memh")
        .orElse(sys.env.get("RV32I_MEMH"))
        .getOrElse(defaultMemh)

    val maxCycles =
      sys.props.get("rv32i.maxCycles")
        .orElse(sys.env.get("RV32I_MAX_CYCLES"))
        .map(_.toInt)
        .getOrElse(200)

    val expectedDir = Paths.get(
      sys.props.get("rv32i.expectedDir")
        .orElse(sys.env.get("RV32I_EXPECTED_DIR"))
        .getOrElse("local-rv32i/expected")
    )

    println(s"[PipelinedRv32iProgramTest] memh=$memhPath maxCycles=$maxCycles expectedDir=$expectedDir")

    runProgram(
      memh = Paths.get(memhPath),
      maxCycles = maxCycles,
      expectedDir = expectedDir
    )
  }
}
