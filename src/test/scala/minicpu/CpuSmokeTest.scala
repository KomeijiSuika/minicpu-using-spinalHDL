package minicpu

import minicpu.core.CpuTop
import org.scalatest.funsuite.AnyFunSuite
import spinal.core.sim._

class CpuSmokeTest extends AnyFunSuite {
  test("CpuTop smoke sim") {
    // SimConfig.compile(): 将 SpinalHDL 设计编译成可仿真的形式
    // - 先把 Scala 代码转换成 Verilog
    // - 再用 Verilator 编译 Verilog
    // - 生成可执行的仿真程序
    // new CpuTop(CpuConfig()): 创建要测试的 CPU 顶层模块实例
    // doSim { dut => ... }: 启动仿真会话，dut 是 Device Under Test（被测设备）的句柄
    SimConfig.compile(new CpuTop(CpuConfig())).doSim { dut =>
      
      // forkStimulus(10): 在后台启动时钟生成器
      // - 参数 10 表示时钟周期为 10 个仿真时间单位（5 高 + 5 低）
      // - fork 表示异步执行，时钟会一直运行到仿真结束
      dut.clockDomain.forkStimulus(10)
      
      // assertReset(): 将复位信号置为有效状态（通常是高电平）
      // - 使 CPU 进入初始化状态
      // - 寄存器被清零，状态机回到初始状态
      dut.clockDomain.assertReset()
      
      // waitSampling(5): 等待 5 个时钟上升沿
      // - 阻塞执行，让复位信号保持有效 5 个时钟周期
      // - 确保 CPU 完全复位
      dut.clockDomain.waitSampling(5)
      
      // deassertReset(): 释放复位信号，变为无效状态（通常是低电平）
      // - CPU 从复位状态恢复
      // - 开始正常工作，可以取指令、执行操作
      dut.clockDomain.deassertReset()
      
      // waitSampling(20): 再等待 20 个时钟周期
      // - 让 CPU 在正常状态下运行一段时间
      // - 观察是否有明显错误（如异常、崩溃）
      // - 这是"冒烟测试"，只验证能否基本运行，不检查功能正确性
      dut.clockDomain.waitSampling(20)
    }
  }
}
