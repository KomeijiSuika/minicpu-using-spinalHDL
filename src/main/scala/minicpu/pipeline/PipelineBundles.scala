package minicpu.pipeline

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig
import minicpu.components.AluOp
import minicpu.mdu.{DivOp, MduOp, MulOp}

// TODO: IF/ID, ID/EX, EX/MEM, MEM/WB 流水线寄存器 Bundles

case class ifIdReg(config: CpuConfig) extends Bundle {
  val pc = UInt(config.xlen bits)
  val instr = UInt(config.xlen bits)
}

case class idExReg(config: CpuConfig) extends Bundle {
    val pc = UInt(config.xlen bits)
    val instr = UInt(config.xlen bits)
    val readAddr1, readAddr2, rd = UInt(5 bits)
    val useRs1, useRs2 = Bool()
    val regData1, regData2 = UInt(config.xlen bits)
    val imm = UInt(32 bits)
    val regWriteEnable = Bool()
    val resultSrc = ResultSrc()
    // memory control 信号
    val memWriteEnable = Bool()
    val loadCtrl = UInt(3 bits)
    val storeCtrl = UInt(3 bits)
    // ALU control 信号
    val aluCtrl = AluOp()
    val aluSrc = UInt(2 bits)
    // branch, jump 信号
    val branchCtrl = UInt(3 bits) 
    val jumpCtrl = UInt(2 bits)  
    // Utype control 信号
    val utypeCtrl = UInt(2 bits)
    val mduOp = MduOp()
    val mulOp = MulOp()
    val divOp = DivOp()
}

case class exMemReg(config: CpuConfig) extends Bundle {
  val pc = UInt(config.xlen bits)
  val rd = UInt(5 bits)
  val aluResult = UInt(config.xlen bits)
  val storeData = UInt(config.xlen bits)
  val regWriteEnable = Bool()
  // memory control 信号
  val memWriteEnable = Bool()
  val loadCtrl = UInt(3 bits)
  val storeCtrl = UInt(3 bits)
}

case class memWbReg(config: CpuConfig) extends Bundle {
  val rd = UInt(5 bits)
  val aluResult = UInt(config.xlen bits)
  val memReadData = UInt(config.xlen bits)
  val loadCtrl = UInt(3 bits)
  val regWriteEnable = Bool()
}

case class mduWbReg(config: CpuConfig) extends Bundle {
  val rd = UInt(5 bits)
  val result = UInt(config.xlen bits)
  val regWrite = Bool()
  val resultSrc = ResultSrc()
}
