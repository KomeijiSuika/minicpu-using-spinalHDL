package minicpu.components

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig

object AluOp extends SpinalEnum {
  val AND     = 0x1.U(4 bits)  // 4'b0001
  val OR      = 0x2.U(4 bits)  // 4'b0010
  val XOR     = 0x3.U(4 bits)  // 4'b0011
  val SLL     = 0x5.U(4 bits)  // 4'b0101
  val SRL     = 0x6.U(4 bits)  // 4'b0110
  val SRA     = 0x7.U(4 bits)  // 4'b0111
  val ADD     = 0x8.U(4 bits)  // 4'b1000
  val SUB     = 0xC.U(4 bits)  // 4'b1100
  val SLT     = 0xD.U(4 bits)  // 4'b1101
  val SLTU    = 0xF.U(4 bits)  // 4'b1111
  val INVALID = 0x0.U(4 bits)  // 4'b0000 - Default/Invalid operation
}

class Alu(config: CpuConfig) extends Component {
  val io = new Bundle {
    val op = in(AluOp())
    val srcA = in UInt(config.xlen bits)
    val srcB = in UInt(config.xlen bits)
    val result = out UInt(config.xlen bits)
  }

  // 默认值（INVALID 或未匹配情况）
  io.result := 0
  
  // 移位量（RV32I 规范：只用低 5 位）
  val shamt = io.srcB(4 downto 0)
  
  switch(io.op) {
    is(AluOp.ADD)  { io.result := io.srcA + io.srcB }
    is(AluOp.SUB)  { io.result := io.srcA - io.srcB }
    is(AluOp.AND)  { io.result := io.srcA & io.srcB }
    is(AluOp.OR)   { io.result := io.srcA | io.srcB }
    is(AluOp.XOR)  { io.result := io.srcA ^ io.srcB }
    is(AluOp.SLL)  { io.result := io.srcA |<< shamt }
    is(AluOp.SRL)  { io.result := io.srcA |>> shamt }
    is(AluOp.SRA)  { io.result := (io.srcA.asSInt >> shamt).asUInt }
    is(AluOp.SLT)  { io.result := U(io.srcA.asSInt < io.srcB.asSInt).resized }
    is(AluOp.SLTU) { io.result := U(io.srcA < io.srcB).resized }
  }
}
