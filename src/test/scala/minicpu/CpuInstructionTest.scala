package minicpu

import minicpu.core.CpuTop
import minicpu.mem.InstrMem
import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.core.sim._
import scala.collection.mutable

/**
 * 综合 CPU 指令测试框架
 * 支持：
 *   - 加载机器码到指令存储器
 *   - 逐步或批量运行（指定周期数）
 *   - 检查寄存器文件中的每个寄存器值
 *   - 检查数据内存中的值
 *   - 检查 PC 值
 */
class CpuInstructionTest extends AnyFunSuite {

  private def simConfig: SpinalSimConfig = {
    SimConfig
      .withVerilator
      .addSimulatorFlag("-CFLAGS")
      .addSimulatorFlag("-std=c++17")
      .addSimulatorFlag("-LDFLAGS")
      .addSimulatorFlag("-std=c++17")
  }

  def createTestSim(testFunc: (SimData) => Unit): Unit = {
    simConfig
      .compile(new CpuTop(CpuConfig()))
      .doSim { dut =>
        val simData = new SimData(dut)
        testFunc(simData)
      }
  }

  // ============== 测试用例 ==============

  /**
   * 测试 ADDI：x1 = 0 + 10，验证结果
   * addi x1, x0, 10   (0x00a00093)
   */
  test("Test ADDI x1, x0, 10") {
    createTestSim { sim =>
      // 加载指令
      val addi_instr = 0x00a00093L // addi x1, x0, 10
      sim.loadInstructions(Seq(addi_instr))

      // 运行
      sim.resetCpu()
      sim.runCycles(10) // 需要足够的周期让指令流经流水线

      // 检查结果
      sim.printRegisters()
      println(s"x1 = ${sim.readRegister(1)} (expected: 10)")
      assert(sim.readRegister(1) == 10, "ADDI 指令执行失败")
    }
  }

  /**
   * 测试 ADD：x2 = x1 + x3
   * 首先用 ADDI 初始化 x1=5, x3=7，然后执行 ADD
   */
  test("Test ADD x2, x1, x3") {
    createTestSim { sim =>
      val instructions = Seq(
        0x00500093L,  // addi x1, x0, 5   (x1 = 5)
        0x00700193L,  // addi x3, x0, 7   (x3 = 7)
        0x00308133L   // add x2, x1, x3   (x2 = x1 + x3 = 12)
      )
      sim.loadInstructions(instructions)

      sim.resetCpu()
      sim.runCycles(20)

      sim.printRegisters()
      println(s"x2 = ${sim.readRegister(2)} (expected: 12)")
      assert(sim.readRegister(2) == 12, "ADD 指令执行失败")
    }
  }

  /**
   * 测试 SUB：x4 = x1 - x3
   * x1=5, x3=7, 期望 x4=-2
   */
  test("Test SUB x4, x1, x3") {
    createTestSim { sim =>
      val instructions = Seq(
        0x00500093L,  // addi x1, x0, 5   (x1 = 5)
        0x00700193L,  // addi x3, x0, 7   (x3 = 7)
        0x40308233L   // sub x4, x1, x3   (x4 = x1 - x3 = -2)
      )
      sim.loadInstructions(instructions)

      sim.resetCpu()
      sim.runCycles(20)

      sim.printRegisters()
      println(s"x4 = ${sim.readRegister(4)} (expected: -2 or 4294967294)")
      
      // assert: Scala 的断言函数，用于验证测试结果
      // 
      // 语法：assert(条件表达式, 错误消息)
      // 
      // 工作原理：
      // 1. 计算条件表达式的布尔值
      // 2. 如果为 true  → 测试通过，继续执行
      // 3. 如果为 false → 抛出 AssertionError 异常，测试失败
      //
      // 在这个例子中：
      // - 读取 x4 寄存器的实际值
      // - 计算期望值：(-2) & 0xFFFFFFFF = 4294967294（32位补码表示）
      // - 比较：实际值 == 期望值？
      //   - 是 → 测试通过 ✓
      //   - 否 → 测试失败 ✗，打印 "SUB 指令执行失败"
      val expected = (-2) & 0xFFFFFFFFL
      assert(sim.readRegister(4) == expected, "SUB 指令执行失败")
    }
  }

  /**
   * 测试 LW（加载字）和 SW（存储字）
   * 向内存写入数据，再读出，验证
   */
  test("Test LW/SW memory operations") {
    createTestSim { sim =>
      val instructions = Seq(
        0x00000113L,  // addi x2, x0, 0   (x2 = 0, 作为地址基数)
        0x01400193L,  // addi x3, x0, 20  (x3 = 20, 要存的数据)
        0x00312023L,  // sw x3, 0(x2)     (内存[0] = 20)
        0x00012283L   // lw x5, 0(x2)     (x5 = 内存[0] = 20)
      )
      sim.loadInstructions(instructions)

      sim.resetCpu()
      sim.runCycles(30)

      sim.printRegisters()
      println(s"x5 (loaded from mem[0]) = ${sim.readRegister(5)} (expected: 20)")
      sim.printDataMemory(0, 4)
      
      // 检查内存值
      val memValue = sim.readDataMemory(0)
      println(s"mem[0] = $memValue (expected: 20)")
      assert(memValue == 20, "SW/LW 操作失败")
      assert(sim.readRegister(5) == 20, "LW 读取失败")
    }
  }

  /**
   * 测试 BEQ（分支相等）
   * if (x1 == x3) PC += 8
   */
  test("Test BEQ (branch equal)") {
    createTestSim { sim =>
      val instructions = Seq(
        0x00500093L,  // addi x1, x0, 5    (x1 = 5)
        0x00500193L,  // addi x3, x0, 5    (x3 = 5)
        0x00308463L,  // beq x1, x3, 8     (x1==x3，跳转到第4条指令)
        0x00100113L,  // addi x2, x0, 1    (x2 = 1, 本应被跳过)
        0x00200113L   // addi x2, x0, 2    (x2 = 2, 分支目标)
      )
      sim.loadInstructions(instructions)

      sim.resetCpu()
      sim.runCycles(30)

      sim.printRegisters()
      println(s"x2 = ${sim.readRegister(2)} (expected: 2, if branch taken)")
      // 如果分支正确，x2 应该是 2；否则是 1
    }
  }

  /**
   * 测试 JAL（跳转并链接）
   * 保存返回地址到 x1，PC 跳转
   */
  test("Test JAL (jump and link)") {
    createTestSim { sim =>
      val instructions = Seq(
        0x00000113L,  // addi x2, x0, 0    (x2 = 0)
        0x00c0006fL,  // jal x1, 12        (x1 = PC + 4 = 4; PC = 4 + 12 = 16 → 指令3)
        0x00100113L,  // addi x2, x0, 1    (本应被跳过)
        0x00200113L   // addi x2, x0, 2    (跳转目标)
      )
      sim.loadInstructions(instructions)

      sim.resetCpu()
      sim.runCycles(30)

      sim.printRegisters()
      println(s"x1 (return addr) = 0x${sim.readRegister(1).toHexString} (expected: 0x4)")
      println(s"x2 = ${sim.readRegister(2)} (expected: 2)")
    }
  }

  /**
   * 测试 JALR（跳转并链接 - 寄存器）
   * 保存返回地址，PC = (x1 + imm) & ~1
   */
  test("Test JALR (jump and link register)") {
    createTestSim { sim =>
      val instructions = Seq(
        0x01000093L,  // addi x1, x0, 16   (x1 = 16, 跳转目标地址)
        0x00008067L,  // jalr x1           (x1 = PC + 4 = 4; PC = x1 = 16 → 指令4)
        0x00100113L,  // addi x2, x0, 1    (本应被跳过)
        0x00100113L,  // padding
        0x00200113L   // addi x2, x0, 2    (跳转目标)
      )
      sim.loadInstructions(instructions)

      sim.resetCpu()
      sim.runCycles(30)

      sim.printRegisters()
      println(s"x1 (return addr) = 0x${sim.readRegister(1).toHexString} (expected: 0x4)")
      println(s"x2 = ${sim.readRegister(2)} (expected: 2)")
    }
  }

  /**
   * 测试 LUI：x1 = imm << 12
   * lui x1, 0x12345 -> x1 = 0x12345000
   */
  test("Test LUI") {
    createTestSim { sim =>
      val instructions = Seq(
        0x123450b7L // lui x1, 0x12345
      )
      sim.loadInstructions(instructions)

      sim.resetCpu()
      sim.runCycles(12)

      val expected = 0x12345000L
      println(s"x1 = 0x${sim.readRegister(1).toHexString} (expected: 0x${expected.toHexString})")
      assert(sim.readRegister(1) == expected, "LUI 指令执行失败")
    }
  }

  /**
   * 测试 AUIPC：x2 = PC + (imm << 12)
   * auipc x2, 0x1 在 PC=0 处执行，期望 x2=0x1000
   */
  test("Test AUIPC") {
    createTestSim { sim =>
      val instructions = Seq(
        0x00001117L // auipc x2, 0x1
      )
      sim.loadInstructions(instructions)

      sim.resetCpu()
      sim.runCycles(12)

      val expected = 0x00001000L
      println(s"x2 = 0x${sim.readRegister(2).toHexString} (expected: 0x${expected.toHexString})")
      assert(sim.readRegister(2) == expected, "AUIPC 指令执行失败")
    }
  }

  /**
   * 自定义测试：用户可在此添加自己的机器码
   */
  test("Custom instruction test") {
    createTestSim { sim =>
      // === 在此处插入你的机器码 ===
      val instructions = Seq(
        0x00500093L,  // addi x1, x0, 5
        0x00100113L,  // addi x2, x0, 1
        0x00208133L   // add x2, x1, x2   (x2 = x1 + x2 = 6)
      )
      
      sim.loadInstructions(instructions)
      sim.resetCpu()
      sim.runCycles(20)
      
      sim.printRegisters()
      sim.printDataMemory(0, 10)
    }
  }

}

/**
 * 辅助类：封装仿真操作
 */
class SimData(dut: CpuTop) {

  /**
   * 加载指令到指令存储器
   * @param instructions 机器码列表（32位无符号整数）
   */
  def loadInstructions(instructions: Seq[Long]): Unit = {
    // 获取 CpuTop 内部的指令存储器对象
    val instrMem = dut.instrMem.instrMem
    
    // zipWithIndex: 将序列中的每个元素与其索引配对
    // 例如: Seq("a", "b", "c").zipWithIndex → Seq(("a", 0), ("b", 1), ("c", 2))
    // 对于指令序列: Seq(0x00a00093, 0x00b00113).zipWithIndex 
    //              → Seq((0x00a00093, 0), (0x00b00113, 1))
    //
    // foreach: 遍历序列中的每个元素，对每个元素执行函数
    // { case (instr, idx) => ... }: 模式匹配，将元组解构为 instr（指令）和 idx（索引）
    //
    // 整体效果：遍历所有指令，同时获得每条指令的内容和它的索引位置
    instructions.zipWithIndex.foreach { case (instr, idx) =>
      // setBigInt(address, value): SpinalHDL 仿真 API
      // 用于在仿真时向 Mem（内存）对象的指定地址写入数据
      // 
      // 前提条件：Mem 必须标记为 simPublic()（在 InstrMem.scala 中已经做了）
      // 
      // 参数：
      // - idx: 内存地址（字地址，不是字节地址）
      // - BigInt(instr): 要写入的值，转换为 BigInt 类型
      //
      // 效果：将机器码写入指令内存的第 idx 个位置
      // 例如：setBigInt(0, 0x00a00093) 将 ADDI 指令写入地址 0
      instrMem.setBigInt(idx, BigInt(instr))
      
      println(s"[加载指令] mem[$idx] = 0x${instr.toHexString}")
    }
    println(s"[加载指令] 成功加载 ${instructions.length} 条指令到指令存储器")
  }

  /**
   * 重置 CPU（清空 PC 和流水线寄存器）
   */
  def resetCpu(): Unit = {
    dut.clockDomain.assertReset()
    dut.clockDomain.waitSampling(5)
    dut.clockDomain.deassertReset()
    dut.clockDomain.waitSampling(2)
    println("[重置] CPU 已重置")
  }

  /**
   * 运行指定数量的时钟周期
   * @param cycles 周期数
   */
  def runCycles(cycles: Int): Unit = {
    println(s"[运行] 执行 $cycles 个时钟周期...")
    dut.clockDomain.waitSampling(cycles)
    println(s"[完成] 已执行 $cycles 个时钟周期")
  }

  /**
   * 读取寄存器的值（通过调试接口）
   * @param regIdx 寄存器索引（0-31）
   * @return 寄存器值
   */
  def readRegister(regIdx: Int): Long = {
    // #= 操作符：SpinalHDL 仿真中的信号赋值操作符
    // 用于在仿真时向硬件信号/端口赋值（类似于 Verilog 中的 force 或 SystemVerilog 中的赋值）
    // 
    // 对比：
    // - Scala 普通赋值 `=`：给 Scala 变量赋值，如 val x = 5
    // - 仿真赋值 `#=`：给硬件信号赋值，如 dut.io.addr #= 10
    //
    // 这里：dut.io.dbg_regAddr #= regIdx
    // 含义：将 regIdx 的值写入到 CPU 的调试接口 dbg_regAddr 端口
    // 效果：就像在硬件仿真中给该信号线施加电压，设置其值
    dut.io.dbg_regAddr #= regIdx
    
    // 等待一个周期让数据稳定
    // 因为硬件是时序逻辑，需要时钟周期来传播信号
    dut.clockDomain.waitSampling()
    
    // .toLong：读取硬件信号的值，并转换为 Scala 的 Long 类型
    // 这是从硬件到软件的数据传输
    dut.io.dbg_regData.toLong
  }

  /**
   * 读取数据内存中指定地址的值（字寻址）
   * @param wordAddr 字地址（不是字节地址）
   * @return 内存值
   */
  def readDataMemory(wordAddr: Int): Long = {
    dut.io.dbg_memAddr #= wordAddr
    dut.clockDomain.waitSampling()
    dut.io.dbg_memData.toLong
  }

  /**
   * 写入数据内存中指定地址的值（字寻址）
   */
  def writeDataMemory(wordAddr: Int, value: Long): Unit = {
    // 使用 #= 给多个硬件信号赋值，模拟内存写操作
    dut.io.dbg_memAddr #= wordAddr           // 设置要写入的内存地址
    dut.io.dbg_memWriteEnable #= true        // 使能写操作（true 在硬件中会转为 1）
    dut.io.dbg_memWriteData #= value         // 设置要写入的数据
    
    // 等待一个时钟周期，让硬件完成写入操作
    dut.clockDomain.waitSampling()
    
    dut.io.dbg_memWriteEnable #= false       // 关闭写使能，结束写操作
    println(s"[内存写入] mem[$wordAddr] = $value")
  }

  /**
   * 读取 PC 值
   */
  def readPC(): Long = {
    dut.io.dbg_pc.toLong
  }

  /**
   * 打印所有寄存器的值（x0-x31）
   */
  def printRegisters(): Unit = {
    println("\n[寄存器文件内容]")
    println("-" * 60)
    for (i <- 0 until 32) {
      val value = readRegister(i)
      val hexValue = f"$value%08x"
      println(f"x$i%-2d = 0x$hexValue ($value)")
    }
    println("-" * 60)
  }

  /**
   * 打印数据内存的内容
   * @param startAddr 起始字地址
   * @param count 打印的字数
   */
  def printDataMemory(startAddr: Int, count: Int): Unit = {
    println(s"\n[数据内存内容] 地址 0x${startAddr}%04x ~ 0x${startAddr + count - 1}%04x")
    println("-" * 60)
    for (i <- 0 until count) {
      val addr = startAddr + i
      val value = readDataMemory(addr)
      val hexValue = f"$value%08x"
      println(f"mem[0x${addr * 4}%08x] = 0x$hexValue ($value)")
    }
    println("-" * 60)
  }

  /**
   * 打印 PC 值
   */
  def printPC(): Unit = {
    val pc = readPC()
    println(s"PC = 0x${f"$pc%08x"}")
  }

  /**
   * 断言：检查寄存器值是否符合预期
   */
  def assertRegister(regIdx: Int, expected: Long, msg: String = ""): Unit = {
    val actual = readRegister(regIdx)
    val msgStr = if (msg.nonEmpty) s" ($msg)" else ""
    assert(actual == expected, s"x$regIdx 应为 0x${f"$expected%08x"}，实际为 0x${f"$actual%08x"}$msgStr")
  }

  /**
   * 断言：检查内存值是否符合预期
   */
  def assertMemory(wordAddr: Int, expected: Long, msg: String = ""): Unit = {
    val actual = readDataMemory(wordAddr)
    val msgStr = if (msg.nonEmpty) s" ($msg)" else ""
    assert(actual == expected, s"mem[$wordAddr] 应为 0x${f"$expected%08x"}，实际为 0x${f"$actual%08x"}$msgStr")
  }
}
