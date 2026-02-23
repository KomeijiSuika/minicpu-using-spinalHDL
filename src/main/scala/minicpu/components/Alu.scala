package minicpu.components

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig

object AluOp {
  // Spinal convention: expose an apply() that returns the signal type.
  def apply() = UInt(4 bits)

  def AND     = U(0x1, 4 bits)  // 4'b0001
  def OR      = U(0x2, 4 bits)  // 4'b0010
  def XOR     = U(0x3, 4 bits)  // 4'b0011
  def SLL     = U(0x5, 4 bits)  // 4'b0101
  def SRL     = U(0x6, 4 bits)  // 4'b0110
  def SRA     = U(0x7, 4 bits)  // 4'b0111
  def ADD     = U(0x8, 4 bits)  // 4'b1000
  def SUB     = U(0xC, 4 bits)  // 4'b1100
  def SLT     = U(0xD, 4 bits)  // 4'b1101
  def SLTU    = U(0xF, 4 bits)  // 4'b1111
  def INVALID = U(0x0, 4 bits)  // 4'b0000 - Default/Invalid operation
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
