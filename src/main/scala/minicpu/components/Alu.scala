package minicpu.components

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig

object AluOp extends SpinalEnum {
  // 定义所有 ALU 操作枚举值
  val AND, OR, XOR, SLL, SRL, SRA, ADD, SUB, SLT, SLTU, INVALID = newElement()
  
  // 自定义编码方案（匹配参考 Verilog 实现）
  defaultEncoding = SpinalEnumEncoding("staticEncoding")(
    AND     -> 0x1,  // 4'b0001
    OR      -> 0x2,  // 4'b0010
    XOR     -> 0x3,  // 4'b0011
    SLL     -> 0x5,  // 4'b0101
    SRL     -> 0x6,  // 4'b0110
    SRA     -> 0x7,  // 4'b0111
    ADD     -> 0x8,  // 4'b1000
    SUB     -> 0xC,  // 4'b1100
    SLT     -> 0xD,  // 4'b1101
    SLTU    -> 0xF,  // 4'b1111
    INVALID -> 0x0   // 4'b0000
  )
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
