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
  /**
    * Minimal `readmemh`-style parser.
    * - Supports comments after `//`.
    * - Supports address directives like `@00000010` (word address).
    * - Treats each non-empty token line as one 32-bit word in hex.
    */
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

  private def runProgram(memh: Path, maxCycles: Int): Unit = {
    requireFileOrCancel(
      memh,
      hint = "Generate it from .s via: python3 -m pip install bitstring && python3 local-rv32i/assembler.py local-rv32i/asm/<name>.s -o local-rv32i/asm/<name>.memh"
    )

    val programName = memh.getFileName.toString.replaceAll("\\.memh$", "")
    val outDir = Paths.get("sim_out")
    Files.createDirectories(outDir)

    SimConfig.withWave.compile(new CpuTop(CpuConfig())).doSim { dut =>
      dut.clockDomain.forkStimulus(period = 10)

      // Reset
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()

      // Clear memories / regfile to deterministic 0s
      for (i <- 0 until 1024) setBigInt(dut.instrMem.instrMem, i.toLong, BigInt(0))
      for (i <- 0 until 1024) setBigInt(dut.dataMem.dataMem, i.toLong, BigInt(0))
      for (i <- 0 until 32) setBigInt(dut.regFile.regFile, i.toLong, BigInt(0))

      // Load program image
      val image = MemhSupport.parseMemhWords(memh)
      image.foreach { case (idx, word) =>
        if (idx >= 0 && idx < 1024) setBigInt(dut.instrMem.instrMem, idx, word)
      }

      // Run
      for (_ <- 0 until maxCycles) {
        dut.clockDomain.waitSampling()
      }

      // Dump regfile
      val regLines = (0 until 32).map { i =>
        val v = getBigInt(dut.regFile.regFile, i.toLong)
        f"x$i%02d = 0x${MemhSupport.hex32(v)}"
      }
      val regPath = outDir.resolve(s"regfile_$programName.txt")
      Files.write(regPath, regLines.asJava, StandardCharsets.UTF_8)

      // Dump data memory (word addressed)
      val memLines = (0 until 1024).map { i =>
        val addr = i * 4
        val v = getBigInt(dut.dataMem.dataMem, i.toLong)
        f"0x$addr%08x : 0x${MemhSupport.hex32(v)}"
      }
      val memPath = outDir.resolve(s"datamem_$programName.txt")
      Files.write(memPath, memLines.asJava, StandardCharsets.UTF_8)

      println(s"[PipelinedRv32iProgramTest] program=$memh cycles=$maxCycles")
      println(s"[PipelinedRv32iProgramTest] reg dump : $regPath")
      println(s"[PipelinedRv32iProgramTest] mem dump : $memPath")
    }
  }

  test("Run local RV32I program (.memh) and dump state") {
    val defaultMemh = "local-rv32i/asm/itypes.memh"
    val memhPath = sys.props.getOrElse("rv32i.memh", defaultMemh)
    val maxCycles = sys.props.get("rv32i.maxCycles").map(_.toInt).getOrElse(200)
    runProgram(Paths.get(memhPath), maxCycles = maxCycles)
  }
}
