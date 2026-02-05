package minicpu.core

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig
import minicpu.components._

class CpuTop(config: CpuConfig) extends Component {
  val io = new Bundle {
    // 定义对外接口，例如 instruction bus, data bus
    val iBus = master(Stream(UInt(32 bits))) // 简化示例
    val dBus = master(Stream(UInt(32 bits))) // 简化示例
  }

  // 组件实例化
  val alu = new Alu(config)

  // 简单的逻辑连接 (TODO: 实现流水线)
  alu.io.op := AluOp.ADD
  alu.io.srcA := 0
  alu.io.srcB := 0

  io.iBus.valid := False
  io.iBus.payload := 0
  io.dBus.valid := False
  io.dBus.payload := alu.io.result
}
