package minicpu.core

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig
import minicpu.components._
import minicpu.mem._
import minicpu.pipeline._
import minicpu.isa._

class CpuTop(config: CpuConfig) extends Component {
  val io = new Bundle {
    // 定义对外接口，例如 instruction bus, data bus
    val iBus = master(Stream(UInt(32 bits))) // 简化示例
    val dBus = master(Stream(UInt(32 bits))) // 简化示例
  }

  // 组件实例化
  val alu = new Alu(config)
  val regFile = new RegFile(config)
  val instrMem = new InstrMem(config)
  val decode = new Decode
  val dataMem = new DataMem(config)
  val forwardUnit = new ForwardUnit(config)
  val hazardUnit = new HazardUnit(config)
  val ifId = Reg(ifIdReg(config)) init(ifIdReg(config).getZero) // IF/ID 流水线寄存器
  val idEx = Reg(idExReg(config)) init(idExReg(config).getZero) // ID/EX 流水线寄存器
  val exMem = Reg(exMemReg(config)) init(exMemReg(config).getZero) // EX/MEM 流水线寄存器
  val memWb = Reg(memWbReg(config)) init(memWbReg(config).getZero) // MEM/WB 流水线寄存器

  val PC = RegInit(U(0, config.xlen bits)) // 程序计数器初始值

  // 先声明 WB 写回值（用于写寄存器/前递）
  // Decode 约定：loadCtrl == 3 表示非 load，其它值表示某种 load
  val wbValue = UInt(config.xlen bits)
  wbValue := Mux(memWb.loadCtrl =/= 3, memWb.memReadData, memWb.aluResult)

  //连线

  // -------------------------
  // Hazard / Control
  // -------------------------

  // hazard unit (load-use)
  hazardUnit.io.idEx := idEx
  hazardUnit.io.exMem := exMem
  hazardUnit.io.memWb := memWb
  hazardUnit.io.idReadAddr1 := decode.io.rs1
  hazardUnit.io.idReadAddr2 := decode.io.rs2

  val loadStall = hazardUnit.io.stall
  val loadFlushE = hazardUnit.io.flush // 插入 bubble 到 EX（清空 ID/EX）

  // EX 阶段源操作数（含前递）
  val exRs1 = UInt(config.xlen bits)
  exRs1 := idEx.regData1
  switch(forwardUnit.io.forwardA) {
    is(1) { exRs1 := exMem.aluResult } // EX/MEM
    is(2) { exRs1 := wbValue }         // MEM/WB
    default { exRs1 := idEx.regData1 }
  }

  val exRs2 = UInt(config.xlen bits)
  exRs2 := idEx.regData2
  switch(forwardUnit.io.forwardB) {
    is(1) { exRs2 := exMem.aluResult } // EX/MEM
    is(2) { exRs2 := wbValue }         // MEM/WB
    default { exRs2 := idEx.regData2 }
  }

  // 分支/跳转在 EX 决策（最小实现：branchCtrl/jumpCtrl 来自 idEx）
  val exBranchTaken = Bool()
  exBranchTaken := False
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
  when(!loadStall) {
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
  } elsewhen (!loadStall) {
    ifId.pc := PC
    ifId.instr := instrMem.io.instr
  } // loadStall 时保持 ifId 不变

  //decode, regFile
  decode.io.inst := ifId.instr
  regFile.io.readAddr1 := decode.io.rs1
  regFile.io.readAddr2 := decode.io.rs2
  regFile.io.writeAddr := memWb.rd
  regFile.io.writeData := wbValue
  regFile.io.writeEnable := memWb.regWriteEnable

  //ID/EX 流水线寄存器
  when(exTaken || loadFlushE) {
    idEx := idExReg(config).getZero
  } elsewhen (!loadStall) {
    idEx.pc := ifId.pc
    idEx.instr := ifId.instr
    idEx.readAddr1 := decode.io.rs1
    idEx.readAddr2 := decode.io.rs2
    idEx.rd := decode.io.rd
    idEx.regData1 := regFile.io.readData1
    idEx.regData2 := regFile.io.readData2
    idEx.imm := decode.io.imm
    idEx.regWriteEnable := decode.io.regWriteEnable
    idEx.memWriteEnable := decode.io.memWriteEnable
    idEx.loadCtrl := decode.io.loadCtrl
    idEx.storeCtrl := decode.io.storeCtrl
    idEx.aluCtrl := decode.io.aluCtrl
    idEx.aluSrc := decode.io.aluSrc
    idEx.branchCtrl := decode.io.branchCtrl
    idEx.jumpCtrl := decode.io.jumpCtrl
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

  // EX/MEM 流水线寄存器
  exMem.pc := idEx.pc
  exMem.rd := idEx.rd
  exMem.aluResult := alu.io.result
  exMem.storeData := exRs2
  exMem.regWriteEnable := idEx.regWriteEnable
  exMem.memWriteEnable := idEx.memWriteEnable
  exMem.loadCtrl := idEx.loadCtrl
  exMem.storeCtrl := idEx.storeCtrl

  // dataMem
  dataMem.io.addr := exMem.aluResult
  dataMem.io.writeData := exMem.storeData // store 数据来自 rs2（含前递）
  dataMem.io.memWriteEnable := exMem.memWriteEnable
  dataMem.io.loadCtrl := exMem.loadCtrl
  dataMem.io.storeCtrl := exMem.storeCtrl

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


  

  


}
