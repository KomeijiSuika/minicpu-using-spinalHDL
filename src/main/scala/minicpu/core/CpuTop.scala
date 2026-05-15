package minicpu.core

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig
import minicpu.components._
import minicpu.mem._
import minicpu.pipeline._
import minicpu.isa._
import minicpu.mdu._

class CpuTop(config: CpuConfig) extends Component {
  val io = new Bundle {
    // 定义对外接口，例如 instruction bus, data bus
    val iBus = master(Stream(UInt(32 bits))) // 简化示例
    val dBus = master(Stream(UInt(32 bits))) // 简化示例
    
    // 调试接口（用于测试）
    val dbg_regAddr = in UInt(5 bits)
    val dbg_regData = out UInt(config.xlen bits)
    val dbg_memAddr = in UInt(config.xlen bits)
    val dbg_memData = out UInt(config.xlen bits)
    val dbg_memWriteEnable = in Bool()
    val dbg_memWriteData = in UInt(config.xlen bits)
    val dbg_pc = out UInt(config.xlen bits)
  }

  // 组件实例化
  val alu = new Alu(config)
  val regFile = new RegFile(config)
  val instrMem = new InstrMem(config)
  val decode = new Decode
  val dataMem = new DataMem(config)
  val forwardUnit = new ForwardUnit(config)
  val hazardUnit = new HazardUnit(config)
  val mulUnit = MulUnit(config).setName("mul_u")
  val divUnit = DivUnit(config).setName("div_u")
  val ifId = Reg(ifIdReg(config)) init(ifIdReg(config).getZero) // IF/ID 流水线寄存器
  val idEx = Reg(idExReg(config)) init(idExReg(config).getZero) // ID/EX 流水线寄存器
  val exMem = Reg(exMemReg(config)) init(exMemReg(config).getZero) // EX/MEM 流水线寄存器
  val memWb = Reg(memWbReg(config)) init(memWbReg(config).getZero) // MEM/WB 流水线寄存器

  val PC = RegInit(U(config.resetVector, config.xlen bits))
  val noLoadCtrl = U(3, 3 bits)
  val noStoreCtrl = U(3, 3 bits)

  //连线

  // -------------------------
  // Hazard / Control
  // -------------------------

  // hazard unit (load-use)
  hazardUnit.io.idEx := idEx
  hazardUnit.io.exMem := exMem
  hazardUnit.io.memWb := memWb
  hazardUnit.io.idReadAddr1 := Mux(decode.io.useRs1, decode.io.rs1, U(0, 5 bits))
  hazardUnit.io.idReadAddr2 := Mux(decode.io.useRs2, decode.io.rs2, U(0, 5 bits))
  hazardUnit.io.idWriteAddr := decode.io.rd
  hazardUnit.io.idResultSrc := decode.io.resultSrc

  // EX 阶段源操作数（含前递）
  val exRs1 = UInt(config.xlen bits)
  switch(forwardUnit.io.forwardA) {
    is(1) { exRs1 := exMem.aluResult } // EX/MEM
    is(2) { exRs1 := Mux(memWb.loadCtrl =/= noLoadCtrl, memWb.memReadData, memWb.aluResult) }
    default { exRs1 := idEx.regData1 }
  }

  val exRs2 = UInt(config.xlen bits)
  switch(forwardUnit.io.forwardB) {
    is(1) { exRs2 := exMem.aluResult } // EX/MEM
    is(2) { exRs2 := Mux(memWb.loadCtrl =/= noLoadCtrl, memWb.memReadData, memWb.aluResult) }
    default { exRs2 := idEx.regData2 }
  }

  mulUnit.io.regDE := idEx
  mulUnit.io.rs1DataE := exRs1.asBits
  mulUnit.io.rs2DataE := exRs2.asBits
  mulUnit.io.stallMulW := hazardUnit.io.stallMulW
  divUnit.io.regDE := idEx
  divUnit.io.rs1DataE := exRs1.asBits
  divUnit.io.rs2DataE := exRs2.asBits
  divUnit.io.stallDivW := hazardUnit.io.stallDivW

  val mulWb = mulUnit.io.wb
  val divWb = divUnit.io.wb
  hazardUnit.io.rdMul := mulUnit.io.rdMul
  hazardUnit.io.rdDiv := divUnit.io.rdDiv
  val mulBusy = (mulUnit.io.resultSrcMul === ResultSrc.mul) && !(mulWb.regWrite && !hazardUnit.io.stallMulW)
  val divBusy = (divUnit.io.resultSrcDiv === ResultSrc.div) && !(divWb.regWrite && !hazardUnit.io.stallDivW)
  hazardUnit.io.mulBusy := mulBusy
  hazardUnit.io.divBusy := divBusy
  hazardUnit.io.regWriteMul := mulWb.regWrite
  hazardUnit.io.regWriteDiv := divWb.regWrite

  val coreWbValue = UInt(config.xlen bits)
  coreWbValue := Mux(memWb.loadCtrl =/= noLoadCtrl, memWb.memReadData, memWb.aluResult)
  val wbWriteEnable = Bool()
  val wbWriteAddr = UInt(5 bits)
  val wbWriteData = UInt(config.xlen bits)
  wbWriteEnable := memWb.regWriteEnable
  wbWriteAddr := memWb.rd
  wbWriteData := coreWbValue
  when(divWb.regWrite) {
    wbWriteEnable := True
    wbWriteAddr := divWb.rd
    wbWriteData := divWb.result
  } elsewhen (mulWb.regWrite) {
    wbWriteEnable := True
    wbWriteAddr := mulWb.rd
    wbWriteData := mulWb.result
  }

  val frontStall = hazardUnit.io.stall
  val loadFlushE = hazardUnit.io.flush

  // 分支/跳转在 EX 决策（最小实现：branchCtrl/jumpCtrl 来自 idEx）
  val exBranchTaken = Bool()
  switch(idEx.branchCtrl) {
    is(1) { exBranchTaken := exRs1 === exRs2 }                     // BEQ
    is(2) { exBranchTaken := exRs1 =/= exRs2 }                     // BNE
    is(3) { exBranchTaken := exRs1.asSInt < exRs2.asSInt }         // BLT
    is(4) { exBranchTaken := exRs1.asSInt >= exRs2.asSInt }        // BGE
    is(5) { exBranchTaken := exRs1 < exRs2 }                       // BLTU
    is(6) { exBranchTaken := exRs1 >= exRs2 }                      // BGEU
    default { exBranchTaken := False }
  }

  val exJumpTaken = idEx.jumpCtrl =/= 0
  val exTaken = exJumpTaken || exBranchTaken

  val exTarget = UInt(config.xlen bits)
  exTarget := (idEx.pc + idEx.imm).resized
  when(idEx.jumpCtrl === 2) { // JALR
    exTarget := ((exRs1 + idEx.imm).resized & ~U(1, config.xlen bits))
  }

  // PC 更新逻辑：load-use stall 时冻结 PC；分支/跳转 taken 时跳转并冲刷前级
  when(!frontStall) {
    PC := (PC + 4).resized
    when(exTaken) {
      PC := exTarget
    }
  }
  //instrMem
  instrMem.io.addr := PC

  //IF/ID 流水线寄存器
  when(exTaken) {
    ifId := ifIdReg(config).getZero
  } elsewhen (!frontStall) {
    ifId.pc := PC
    ifId.instr := instrMem.io.instr
  } // loadStall 时保持 ifId 不变

  //decode, regFile
  decode.io.inst := ifId.instr
  regFile.io.readAddr1 := decode.io.rs1
  regFile.io.readAddr2 := decode.io.rs2
  regFile.io.writeAddr := wbWriteAddr
  regFile.io.writeData := wbWriteData
  regFile.io.writeEnable := wbWriteEnable

  // ID 阶段读数 + WB 同拍旁路（修正 regfile 同拍写回可见性）
  val idRs1Data = UInt(config.xlen bits)
  idRs1Data := regFile.io.readData1
  when(wbWriteEnable && (wbWriteAddr =/= 0) && (wbWriteAddr === decode.io.rs1)) {
    idRs1Data := wbWriteData
  }

  val idRs2Data = UInt(config.xlen bits)
  idRs2Data := regFile.io.readData2
  when(wbWriteEnable && (wbWriteAddr =/= 0) && (wbWriteAddr === decode.io.rs2)) {
    idRs2Data := wbWriteData
  }

  //ID/EX 流水线寄存器
  when(exTaken || loadFlushE) {
    idEx := idExReg(config).getZero
  } elsewhen (!frontStall) {
    idEx.pc := ifId.pc
    idEx.instr := ifId.instr
    idEx.readAddr1 := decode.io.rs1
    idEx.readAddr2 := decode.io.rs2
    idEx.useRs1 := decode.io.useRs1
    idEx.useRs2 := decode.io.useRs2
    idEx.rd := decode.io.rd
    idEx.regData1 := idRs1Data
    idEx.regData2 := idRs2Data
    idEx.imm := decode.io.imm
    idEx.regWriteEnable := decode.io.regWriteEnable
    idEx.resultSrc := decode.io.resultSrc
    idEx.memWriteEnable := decode.io.memWriteEnable
    idEx.loadCtrl := decode.io.loadCtrl
    idEx.storeCtrl := decode.io.storeCtrl
    idEx.aluCtrl := decode.io.aluCtrl
    idEx.aluSrc := decode.io.aluSrc
    idEx.branchCtrl := decode.io.branchCtrl
    idEx.jumpCtrl := decode.io.jumpCtrl
    idEx.utypeCtrl := decode.io.utypeCtrl
    idEx.mduOp := decode.io.mduOp
    idEx.mulOp := decode.io.mulOp
    idEx.divOp := decode.io.divOp
  } // loadStall 时：保持 idEx 不变；但 loadFlushE 会把它清成 bubble

  // forward unit
  forwardUnit.io.idEx := idEx
  forwardUnit.io.exMem := exMem
  forwardUnit.io.memWb := memWb

  // ALU
  // Decode.aluSrc 编码：0=Reg, 1=Imm, 2=PC
  // - Reg:  srcA=rs1, srcB=rs2
  // - Imm:  srcA=rs1, srcB=imm
  // - PC :  srcA=PC,  srcB=imm
  alu.io.op := idEx.aluCtrl
  alu.io.srcA := Mux(idEx.aluSrc === 2, idEx.pc, exRs1)
  alu.io.srcB := Mux(idEx.aluSrc === 0, exRs2, idEx.imm)

  val uNone  = U(0, 2 bits)
  val uLui   = U(1, 2 bits)
  val uAuipc = U(2, 2 bits)

  val exWbResult = UInt(config.xlen bits)
  exWbResult := alu.io.result
  when(idEx.utypeCtrl === uLui) {
    exWbResult := idEx.imm
  } elsewhen(idEx.utypeCtrl === uAuipc) {
    exWbResult := (idEx.pc + idEx.imm).resized
  } elsewhen(idEx.jumpCtrl =/= 0) {
    exWbResult := (idEx.pc + 4).resized
  } 


  // EX/MEM 流水线寄存器
  when(idEx.resultSrc === ResultSrc.mul || idEx.resultSrc === ResultSrc.div) {
    exMem := exMemReg(config).getZero
  } otherwise {
    exMem.pc := idEx.pc
    exMem.rd := idEx.rd
    exMem.aluResult := exWbResult
    exMem.storeData := exRs2
    exMem.regWriteEnable := idEx.regWriteEnable
    exMem.memWriteEnable := idEx.memWriteEnable
    exMem.loadCtrl := idEx.loadCtrl
    exMem.storeCtrl := idEx.storeCtrl
  }

  // dataMem
  dataMem.io.addr := exMem.aluResult
  dataMem.io.writeData := exMem.storeData // store 数据来自 rs2（含前递）
  dataMem.io.memWriteEnable := exMem.memWriteEnable
  dataMem.io.loadCtrl := exMem.loadCtrl
  dataMem.io.storeCtrl := exMem.storeCtrl
  // dataMem debug 端口
  dataMem.io.dbgAddr := io.dbg_memAddr
  dataMem.io.dbgWriteEnable := io.dbg_memWriteEnable
  dataMem.io.dbgWriteData := io.dbg_memWriteData

  // MEM/WB 流水线寄存器
  memWb.rd := exMem.rd
  memWb.aluResult := exMem.aluResult
  memWb.memReadData := dataMem.io.readData
  memWb.loadCtrl := exMem.loadCtrl
  memWb.regWriteEnable := exMem.regWriteEnable

  // 顶层总线（未实现时先拉低）
  io.iBus.valid := False
  io.iBus.payload := 0
  io.dBus.valid := False
  io.dBus.payload := 0

  // ========================
  // 调试接口实现
  // ========================
  // 寄存器读取（使用专用调试读端口）
  regFile.io.dbg_readAddr := io.dbg_regAddr
  io.dbg_regData := regFile.io.dbg_readData
  io.dbg_memData := dataMem.io.dbgReadData
  io.dbg_pc := PC
}
