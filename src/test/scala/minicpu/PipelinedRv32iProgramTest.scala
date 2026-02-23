package minicpu

import minicpu.core.CpuTop
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.exceptions.TestCanceledException
import spinal.core.sim._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.io.{BufferedReader, InputStreamReader}
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
 * .memh 文件解析支持
 * 
 * .memh 文件格式（Verilog $readmemh 兼容格式）：
 * - 每行一个 32 位十六进制数（8 个字符）
 * - 支持 // 注释
 * - 支持地址指令 @00000010（指定起始地址）
 * 
 * 示例：
 *   01100093 // PC=0x0 line=1: addi x1, x0, 17
 *   00409113 // PC=0x4 line=2: slli x2, x1, 4
 *   @00000010
 *   3e812193 // 从地址 0x10 开始
 */
object MemhSupport {
  /**
   * 解析 .memh 文件为 (地址, 数据) 序列
   * 
   * @param memhPath .memh 文件路径
   * @return 序列：(字地址, 32位数据值)
   * 
   * 例如：itypes.memh 会被解析为：
   *   (0, 0x01100093),  // addi x1, x0, 17
   *   (1, 0x00409113),  // slli x2, x1, 4
   *   (2, 0x3e812193),  // slti x3, x2, 1000
   *   ...
   */
  def parseMemhWords(memhPath: Path): Seq[(Long, BigInt)] = {
    val out = ArrayBuffer.empty[(Long, BigInt)]
    var wordIndex = 0L  // 当前字地址（0, 1, 2, ...）

    // 读取文件的所有行
    val lines = Files.readAllLines(memhPath, StandardCharsets.UTF_8).asScala
    for (raw <- lines) {
      // 移除注释（// 之后的所有内容）
      val cut = raw.indexOf("//")
      val noComment = (if (cut >= 0) raw.substring(0, cut) else raw).trim
      
      if (noComment.nonEmpty) {
        // 处理地址指令：@00000010 表示跳转到地址 0x10
        if (noComment.startsWith("@")) {
          val addrHex = noComment.drop(1).trim  // 去掉 @
          wordIndex = BigInt(addrHex, 16).toLong  // 解析为十六进制数
        } else {
          // 处理数据行：01100093
          val hex = noComment.replace("_", "")  // 移除下划线分隔符（如果有）
          val value = BigInt(hex, 16) & BigInt("ffffffff", 16)  // 解析为 32 位数
          out += wordIndex -> value  // 添加到结果
          wordIndex += 1  // 地址递增
        }
      }
    }
    out.toSeq
  }

  /**
   * 将 BigInt 转换为 8 位十六进制字符串
   * @param value 要转换的值
   * @return 格式化的字符串，例如 "01100093"
   */
  def hex32(value: BigInt): String = {
    val masked = value & BigInt("ffffffff", 16)  // 保留低 32 位
    val s = masked.toString(16)  // 转换为十六进制字符串
    "0" * (8 - s.length) + s  // 前面补 0 到 8 位
  }
}

/**
 * 综合程序测试：加载 .memh 文件到 CPU 并运行
 * 
 * 完整流程：
 * 1. 编写汇编代码 (.s 文件)
 * 2. 使用 Python 汇编器生成机器码 (.memh 文件)
 * 3. 测试加载 .memh 到指令内存
 * 4. 运行 CPU 指定周期数
 * 5. 导出寄存器和内存状态到文本文件
 * 6. 手动检查输出文件验证结果
 */
class PipelinedRv32iProgramTest extends AnyFunSuite {
  // 辅助函数：检查文件是否存在，不存在则跳过测试
  private def requireFileOrCancel(path: Path, hint: String): Unit = {
    if (!Files.exists(path)) {
      throw new TestCanceledException(
        s"Missing file: $path\n$hint",
        0
      )
    }
  }

  /**
   * 运行外部进程并收集输出。
   *
   * 返回值：
   * - Int: 进程退出码（0 表示成功）
   * - String: 标准输出 + 标准错误（已合并）
   *
   * 用途：调用 Python 汇编器时，把日志完整拿回来，失败时直接显示给测试报告。
   */
  private def runAndCaptureOutput(pb: ProcessBuilder): (Int, String) = {
    // redirectErrorStream(true): 将 stderr 合并到 stdout，便于一次性采集日志
    val process = pb.redirectErrorStream(true).start()
    val reader = new BufferedReader(new InputStreamReader(process.getInputStream, StandardCharsets.UTF_8))
    val log = new StringBuilder
    var line = reader.readLine()
    while (line != null) {
      log.append(line).append("\n")
      line = reader.readLine()
    }
    val code = process.waitFor()
    (code, log.toString())
  }

  private def firstDiffIndex(a: Seq[String], b: Seq[String]): Int = {
    val n = Math.min(a.length, b.length)
    var i = 0
    while (i < n) {
      if (a(i) != b(i)) return i
      i += 1
    }
    if (a.length != b.length) n else -1
  }

  /**
   * 写出当前仿真结果，并根据 expected 文件进行自动比对。
   *
   * 策略：
   * - expected 存在：逐行比较，不一致则测试失败。
   * - expected 不存在且允许生成：将当前结果写为 expected。
   * - expected 不存在且不允许生成：抛出提示，让用户先生成 expected。
   */
  private def writeAndVerifyAgainstExpected(
      kind: String,
      programName: String,
      lines: Seq[String],
      outDir: Path,
      expectedDir: Path,
      generateExpectedIfMissing: Boolean
  ): Unit = {
    val outPath = outDir.resolve(s"${kind}_${programName}.txt")
    Files.write(outPath, lines.asJava, StandardCharsets.UTF_8)

    val expectedPath = expectedDir.resolve(s"${kind}_${programName}.txt")
    if (!Files.exists(expectedPath)) {
      if (generateExpectedIfMissing) {
        Files.createDirectories(expectedDir)
        Files.write(expectedPath, lines.asJava, StandardCharsets.UTF_8)
        println(s"[PipelinedRv32iProgramTest] generated expected: $expectedPath")
      } else {
        throw new TestCanceledException(
          s"Missing expected file: $expectedPath\n" +
            s"Run once with -Drv32i.genExpected=true (or RV32I_GEN_EXPECTED=true) to generate baseline.",
          0
        )
      }
    } else {
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
  }

  /**
   * 把 .s 汇编文件转换为 .memh。
   *
   * 输入：  local-rv32i/asm/xxx.s
   * 输出：  sim_out/generated_memh/xxx.memh
   *
   * 注意：这里依赖 python3 + local-rv32i/assembler.py。
   * 若缺少 Python 依赖（如 bitstring），会抛出 RuntimeException，并把汇编器日志打印出来。
   */
  private def assembleSourceToMemh(source: Path): Path = {
    // 固定使用仓库内提供的本地汇编器
    val assembler = Paths.get("local-rv32i", "assembler.py")
    requireFileOrCancel(
      assembler,
      hint = "Expected local-rv32i assembler at local-rv32i/assembler.py"
    )
    requireFileOrCancel(
      source,
      hint = s"Source file not found: $source"
    )

    // 自动生成的 memh 放到 sim_out/generated_memh，避免污染 asm 原目录
    val generatedDir = Paths.get("sim_out", "generated_memh")
    Files.createDirectories(generatedDir)
    val baseName = source.getFileName.toString.replaceAll("\\.s$", "")
    val outMemh = generatedDir.resolve(s"$baseName.memh")

    // 等价命令：python3 local-rv32i/assembler.py <source.s> -o <out.memh>
    val cmd = Seq(
      "python3",
      assembler.toString,
      source.toString,
      "-o",
      outMemh.toString
    )
    val (exitCode, output) = runAndCaptureOutput(new ProcessBuilder(cmd: _*))

    // 汇编失败：直接失败测试，并附上完整日志（方便定位是语法错还是环境依赖缺失）
    if (exitCode != 0) {
      throw new RuntimeException(
        s"Assembler failed (exit=$exitCode)\nCommand: ${cmd.mkString(" ")}\n$output"
      )
    }

    // 汇编返回 0 还要再确认输出文件确实存在（防止静默失败）
    requireFileOrCancel(
      outMemh,
      hint = s"Assembler did not produce output memh: $outMemh"
    )
    outMemh
  }

  /**
   * 将用户输入统一解析为“最终可加载的 .memh 文件路径”。
   *
   * 支持三类输入：
   * 1) 直接给 .memh
   * 2) 直接给 .s（会自动汇编）
   * 3) 不带后缀的前缀路径（优先找 .memh，不存在再找 .s）
   */
  private def resolveProgramImage(input: Path): Path = {
    val text = input.toString

    // 情况 1：明确给了 .memh，直接使用
    if (text.endsWith(".memh")) {
      requireFileOrCancel(
        input,
        hint = s"Missing .memh: $input; try providing a .s file and let test auto-assemble it"
      )
      input

    // 情况 2：明确给了 .s，先自动汇编再返回生成的 .memh
    } else if (text.endsWith(".s")) {
      assembleSourceToMemh(input)
    } else {

      // 情况 3：没写后缀，自动补全尝试
      val memh = Paths.get(s"$text.memh")
      val src = Paths.get(s"$text.s")

      // 约定优先级：已有 .memh 优先（可复用预生成结果），其次 .s
      if (Files.exists(memh)) memh
      else if (Files.exists(src)) assembleSourceToMemh(src)
      else {
        throw new TestCanceledException(
          s"Program input not found as .memh or .s: $input\nTried: $memh and $src",
          0
        )
      }
    }
  }

  /**
   * 运行一个完整的 RV32I 程序
   * 
   * @param memh .memh 文件路径（包含机器码的十六进制文本文件）
   * @param maxCycles 最大运行周期数
   */
  private def runProgram(
      memh: Path,
      maxCycles: Int,
      expectedDir: Path,
      generateExpectedIfMissing: Boolean
  ): Unit = {
    // 步骤 1：检查 .memh 文件是否存在
    requireFileOrCancel(
      memh,
      hint = "Generate it from .s via: python3 -m pip install bitstring && python3 local-rv32i/assembler.py local-rv32i/asm/<name>.s -o local-rv32i/asm/<name>.memh"
    )

    val programName = memh.getFileName.toString.replaceAll("\\.memh$", "")
    val outDir = Paths.get("sim_out")
    Files.createDirectories(outDir)  // 创建输出目录

    // 步骤 2：编译并启动仿真（带波形）
    SimConfig.withWave.compile(new CpuTop(CpuConfig())).doSim { dut =>
      // 步骤 3：启动时钟生成器（10ns 周期）
      dut.clockDomain.forkStimulus(period = 10)

      // 步骤 4：复位 CPU
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)   // 保持复位 5 个周期
      dut.clockDomain.deassertReset()   // 释放复位

      // 步骤 5：清空所有内存和寄存器为 0（确保初始状态干净）
      // 清空指令内存（1024 个字）
      for (i <- 0 until 1024) setBigInt(dut.instrMem.instrMem, i.toLong, BigInt(0))
      // 清空数据内存（1024 个字）
      for (i <- 0 until 1024) setBigInt(dut.dataMem.dataMem, i.toLong, BigInt(0))
      // 清空寄存器文件（32 个寄存器）
      for (i <- 0 until 32) setBigInt(dut.regFile.regFile, i.toLong, BigInt(0))

      // 步骤 6：加载程序到指令内存
      // parseMemhWords(): 解析 .memh 文件，返回 (地址, 指令) 对的序列
      // .memh 格式示例：
      //   01100093 // PC=0x0 line=1: addi x1, x0, 17
      //   00409113 // PC=0x4 line=2: slli x2, x1, 4
      val image = MemhSupport.parseMemhWords(memh)
      image.foreach { case (idx, word) =>
        // idx: 字地址（0, 1, 2, ...）
        // word: 32位指令机器码
        if (idx >= 0 && idx < 1024) setBigInt(dut.instrMem.instrMem, idx, word)
      }

      // 步骤 7：运行 CPU 指定的时钟周期数
      for (_ <- 0 until maxCycles) {
        dut.clockDomain.waitSampling()  // 每次等待 1 个时钟周期
      }

      // 步骤 8：导出寄存器状态到文件
      // 读取所有 32 个寄存器的值
      val regLines = (0 until 32).map { i =>
        val v = getBigInt(dut.regFile.regFile, i.toLong)
        f"x$i%02d = 0x${MemhSupport.hex32(v)}"
      }
      // 写入并比对寄存器快照
      writeAndVerifyAgainstExpected(
        kind = "regfile",
        programName = programName,
        lines = regLines,
        outDir = outDir,
        expectedDir = expectedDir,
        generateExpectedIfMissing = generateExpectedIfMissing
      )

      // 步骤 9：导出数据内存状态到文件
      // 读取所有 1024 个字的数据内存
      val memLines = (0 until 1024).map { i =>
        val addr = i * 4  // 字地址转字节地址（乘以 4）
        val v = getBigInt(dut.dataMem.dataMem, i.toLong)
        f"0x$addr%08x : 0x${MemhSupport.hex32(v)}"
      }
      // 写入并比对数据内存快照
      writeAndVerifyAgainstExpected(
        kind = "datamem",
        programName = programName,
        lines = memLines,
        outDir = outDir,
        expectedDir = expectedDir,
        generateExpectedIfMissing = generateExpectedIfMissing
      )

      // 步骤 10：输出测试信息
      println(s"[PipelinedRv32iProgramTest] program=$memh cycles=$maxCycles")
      val regPath = outDir.resolve(s"regfile_$programName.txt")
      val memPath = outDir.resolve(s"datamem_$programName.txt")
      println(s"[PipelinedRv32iProgramTest] reg dump : $regPath")
      println(s"[PipelinedRv32iProgramTest] mem dump : $memPath")
      println(s"[PipelinedRv32iProgramTest] expected dir: $expectedDir")
      
      // 验证方式：自动比较
      // - 若 expected 存在：自动逐行比对，不一致直接失败
      // - 若 expected 不存在且开启生成：自动生成 expected
    }
  }

  /**
   * 主测试用例：运行 local-rv32i 程序
   * 
   * 使用方式：
   * 1. 默认运行 local-rv32i/asm/itypes.memh
   * 2. 可通过系统属性指定其他程序：
   *    sbt "testOnly *PipelinedRv32iProgramTest -- -Drv32i.memh=local-rv32i/asm/btypes.memh"
   * 3. 可指定最大运行周期：
   *    sbt "testOnly *PipelinedRv32iProgramTest -- -Drv32i.maxCycles=500"
   */
  test("Run local RV32I program (.memh) and dump state") {
    // 支持两种输入：
    // 1) rv32i.program=...  (推荐，可传 .s 或 .memh)
    // 2) rv32i.memh=...     (兼容旧参数，仅传 .memh)
    val defaultProgram = "local-rv32i/asm/itypes.memh"

    // 参数优先级（从高到低）：
    // 1) JVM 属性 rv32i.program / rv32i.memh
    // 2) 兼容键名 rv32i_program
    // 3) 环境变量 RV32I_PROGRAM
    // 4) 默认值 local-rv32i/asm/itypes.memh
    val programInput =
      sys.props.get("rv32i.program")
        .orElse(sys.props.get("rv32i.memh"))
        .orElse(sys.props.get("rv32i_program"))
        .orElse(sys.env.get("RV32I_PROGRAM"))
        .getOrElse(defaultProgram)
    val memhPath = resolveProgramImage(Paths.get(programInput))
    
    // 获取最大运行周期数（默认：200）
    val maxCycles =
      sys.props.get("rv32i.maxCycles")
        .orElse(sys.props.get("rv32i_maxCycles"))
        .orElse(sys.env.get("RV32I_MAX_CYCLES"))
        .map(_.toInt)
        .getOrElse(200)

    // 期望结果目录（默认 local-rv32i/expected）
    val expectedDir = Paths.get(
      sys.props.get("rv32i.expectedDir")
        .orElse(sys.env.get("RV32I_EXPECTED_DIR"))
        .getOrElse("local-rv32i/expected")
    )

    // 若 expected 缺失，是否自动生成（默认 false，避免无意把错误结果固化成“正确结果”）
    val generateExpectedIfMissing =
      sys.props.get("rv32i.genExpected")
        .orElse(sys.env.get("RV32I_GEN_EXPECTED"))
        .exists(_.equalsIgnoreCase("true"))

    // 打印“输入参数 -> 最终使用的 memh”映射，方便确认自动汇编是否生效
    println(s"[PipelinedRv32iProgramTest] requested input=$programInput resolved memh=$memhPath")
    println(s"[PipelinedRv32iProgramTest] expectedDir=$expectedDir genExpected=$generateExpectedIfMissing")
    
    // 运行程序（如果传入 .s，会先自动汇编成 sim_out/generated_memh/*.memh）
    runProgram(
      memh = memhPath,
      maxCycles = maxCycles,
      expectedDir = expectedDir,
      generateExpectedIfMissing = generateExpectedIfMissing
    )
  }
}
