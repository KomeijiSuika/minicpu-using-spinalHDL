package minicpu.isa

import spinal.core._
import spinal.lib._
import Rv32iEncoding._
import minicpu.components.AluOp

class Decode extends Component {
  val io = new Bundle {
    // in
    val inst = in UInt(32 bits)

    // out
    val rs1, rs2, rd = out UInt(5 bits)
    val imm = out UInt(32 bits)
    val regWriteEnable = out Bool()
    // memory control 信号
    val memWriteEnable = out Bool()
    val loadCtrl = out UInt(3 bits)
    val storeCtrl = out UInt(3 bits)
    // ALU control 信号
    val aluCtrl = out(AluOp())
    val aluSrc = out UInt(2 bits)
    // branch, jump 信号
    val branchCtrl = out UInt(3 bits) 
    val jumpCtrl = out UInt(2 bits)  
  }
  
  // 默认值，避免 latch
  io.rs1 := Rv32iExtractor.getRs1(io.inst)
  io.rs2 := Rv32iExtractor.getRs2(io.inst)
  io.rd := Rv32iExtractor.getRd(io.inst)
  io.imm := 0
  io.aluCtrl := AluOp.INVALID
  io.aluSrc := 0
  io.branchCtrl := 0
  io.jumpCtrl := 0
  io.regWriteEnable := False
  io.memWriteEnable := False
  io.loadCtrl := 3
  io.storeCtrl := 3

  val opcode = Rv32iExtractor.getOpcode(io.inst)
  val funct3 = Rv32iExtractor.getFunct3(io.inst)
  val funct7 = Rv32iExtractor.getFunct7(io.inst)

  val aluSrcReg = U(0, 2 bits)
  val aluSrcImm = U(1, 2 bits)
  val aluSrcPc  = U(2, 2 bits)

  val brNone = U(0, 3 bits)
  val brEq   = U(1, 3 bits)
  val brNe   = U(2, 3 bits)
  val brLt   = U(3, 3 bits)
  val brGe   = U(4, 3 bits)
  val brLtu  = U(5, 3 bits)
  val brGeu  = U(6, 3 bits)

  val jNone = U(0, 2 bits)
  val jJal  = U(1, 2 bits)
  val jJalr = U(2, 2 bits)

  switch(opcode){
    is(OpType.LOAD) {
      io.imm := Rv32iExtractor.getImm12I(io.inst)
      io.regWriteEnable := True
      io.aluCtrl := AluOp.ADD
      io.aluSrc := aluSrcImm
      switch(funct3) {
        is(Funct3Load.LB)  { io.loadCtrl := 0 }
        is(Funct3Load.LH)  { io.loadCtrl := 1 }
        is(Funct3Load.LW)  { io.loadCtrl := 2 }
        is(Funct3Load.LBU) { io.loadCtrl := 4 }
        is(Funct3Load.LHU) { io.loadCtrl := 5 }
      }
    }
    is(OpType.STORE) {
      io.imm := Rv32iExtractor.getImmS(io.inst)
      io.memWriteEnable := True
      io.aluCtrl := AluOp.ADD
      io.aluSrc := aluSrcImm
      switch(funct3) {
        is(Funct3Store.SB) { io.storeCtrl := 0 }
        is(Funct3Store.SH) { io.storeCtrl := 1 }
        is(Funct3Store.SW) { io.storeCtrl := 2 }
      }
    }
    is(OpType.IMMOP) {
      io.imm := Rv32iExtractor.getImm12I(io.inst)
      io.regWriteEnable := True
      io.aluSrc := aluSrcImm
      switch(funct3) {
        is(Funct3IType.ADD_SUB) { io.aluCtrl := AluOp.ADD }
        is(Funct3IType.SLL)     { io.aluCtrl := AluOp.SLL }
        is(Funct3IType.SLT)     { io.aluCtrl := AluOp.SLT }
        is(Funct3IType.SLTU)    { io.aluCtrl := AluOp.SLTU }
        is(Funct3IType.XOR)     { io.aluCtrl := AluOp.XOR }
        is(Funct3IType.SRL_SRA) {
          when(funct7 === Funct7Type.ALT) { io.aluCtrl := AluOp.SRA }
            .otherwise { io.aluCtrl := AluOp.SRL }
        }
        is(Funct3IType.OR)      { io.aluCtrl := AluOp.OR }
        is(Funct3IType.AND)     { io.aluCtrl := AluOp.AND }
      }
    }
    is(OpType.REGOP) {
      io.regWriteEnable := True
      io.aluSrc := aluSrcReg
      switch(funct3) {
        is(Funct3IType.ADD_SUB) {
          when(funct7 === Funct7Type.ALT) { io.aluCtrl := AluOp.SUB }
            .otherwise { io.aluCtrl := AluOp.ADD }
        }
        is(Funct3IType.SLL)     { io.aluCtrl := AluOp.SLL }
        is(Funct3IType.SLT)     { io.aluCtrl := AluOp.SLT }
        is(Funct3IType.SLTU)    { io.aluCtrl := AluOp.SLTU }
        is(Funct3IType.XOR)     { io.aluCtrl := AluOp.XOR }
        is(Funct3IType.SRL_SRA) {
          when(funct7 === Funct7Type.ALT) { io.aluCtrl := AluOp.SRA }
            .otherwise { io.aluCtrl := AluOp.SRL }
        }
        is(Funct3IType.OR)      { io.aluCtrl := AluOp.OR }
        is(Funct3IType.AND)     { io.aluCtrl := AluOp.AND }
      }
    }
    is(OpType.BRANCH) {
      io.imm := Rv32iExtractor.getImmB(io.inst)
      io.branchCtrl := brNone
      switch(funct3) {
        is(Funct3Branch.BEQ)  { io.branchCtrl := brEq }
        is(Funct3Branch.BNE)  { io.branchCtrl := brNe }
        is(Funct3Branch.BLT)  { io.branchCtrl := brLt }
        is(Funct3Branch.BGE)  { io.branchCtrl := brGe }
        is(Funct3Branch.BLTU) { io.branchCtrl := brLtu }
        is(Funct3Branch.BGEU) { io.branchCtrl := brGeu }
      }
      io.aluCtrl := AluOp.SUB
      io.aluSrc := aluSrcReg
    }
    is(OpType.JAL) {
      io.imm := Rv32iExtractor.getImmJ(io.inst)
      io.regWriteEnable := True
      io.jumpCtrl := jJal
      io.aluCtrl := AluOp.ADD
      io.aluSrc := aluSrcPc
    }
    is(OpType.JALR) {
      io.imm := Rv32iExtractor.getImm12I(io.inst)
      io.regWriteEnable := True
      io.jumpCtrl := jJalr
      io.aluCtrl := AluOp.ADD
      io.aluSrc := aluSrcImm
    }
    is(OpType.LUI) {
      io.imm := Rv32iExtractor.getImmU(io.inst)
      io.regWriteEnable := True
      io.aluCtrl := AluOp.ADD
      io.aluSrc := aluSrcImm
    }
    is(OpType.AUIPC) {
      io.imm := Rv32iExtractor.getImmU(io.inst)
      io.regWriteEnable := True
      io.aluCtrl := AluOp.ADD
      io.aluSrc := aluSrcPc
    }
    default {
      io.branchCtrl := brNone
      io.jumpCtrl := jNone
    }
  }

  

}
