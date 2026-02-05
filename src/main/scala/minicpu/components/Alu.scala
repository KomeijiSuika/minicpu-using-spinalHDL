package minicpu.components

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig

object AluOp extends SpinalEnum {
  val ADD, SUB, AND, OR, XOR = newElement()
  // 更多操作后续添加
}

class Alu(config: CpuConfig) extends Component {
  val io = new Bundle {
    val op = in(AluOp())
    val srcA = in UInt(config.xlen bits)
    val srcB = in UInt(config.xlen bits)
    val result = out UInt(config.xlen bits)
  }

  io.result := 0
  switch(io.op) {
    is(AluOp.ADD) { io.result := io.srcA + io.srcB }
    is(AluOp.SUB) { io.result := io.srcA - io.srcB }
    is(AluOp.AND) { io.result := io.srcA & io.srcB }
    is(AluOp.OR)  { io.result := io.srcA | io.srcB }
    is(AluOp.XOR) { io.result := io.srcA ^ io.srcB }
  }
}
