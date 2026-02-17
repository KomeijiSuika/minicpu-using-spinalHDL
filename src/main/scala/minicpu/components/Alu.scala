package minicpu.components

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig

object AluOp extends SpinalEnum {
  val ADD, SUB, AND, OR, XOR, logicShiftLeft, logicShiftRight, arithShiftRight, lessThan, lessThanUnsigned = newElement()
  
}

class Alu(config: CpuConfig) extends Component {
  val io = new Bundle {
    val op = in(AluOp())
    val srcA = in UInt(config.xlen bits)
    val srcB_imm = in UInt(config.xlen bits)
    val result = out UInt(config.xlen bits)
  }

  io.result := 0
  switch(io.op) {
    is(AluOp.ADD) { io.result := io.srcA + io.srcB_imm }
    is(AluOp.SUB) { io.result := io.srcA - io.srcB_imm }
    is(AluOp.AND) { io.result := io.srcA & io.srcB_imm }
    is(AluOp.OR)  { io.result := io.srcA | io.srcB_imm }
    is(AluOp.XOR) { io.result := io.srcA ^ io.srcB_imm }
    is(AluOp.logicShiftLeft) { io.result := io.srcA |<< io.srcB_imm(4 downto 0) }
    is(AluOp.logicShiftRight) { io.result := io.srcA |>> io.srcB_imm(4 downto 0) }
    is(AluOp.arithShiftRight) { io.result := (io.srcA.asSInt >> io.srcB_imm(4 downto 0)).asUInt }
    is(AluOp.lessThan) { io.result := (io.srcA.asSInt < io.srcB_imm.asSInt).asUInt }
    is(AluOp.lessThanUnsigned) { io.result := (io.srcA < io.srcB_imm).asUInt }
  }
}
