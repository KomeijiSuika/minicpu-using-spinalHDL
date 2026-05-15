package minicpu.pipeline

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig
import minicpu.pipeline.ResultSrc

class HazardUnit(config: CpuConfig) extends Component {
  val io = new Bundle {
    val idEx = in(idExReg(config))
    val exMem = in(exMemReg(config))
    val memWb = in(memWbReg(config))

    // ID 阶段（当前 decode、下一拍要进入 EX 的指令）的源寄存器号
    // 用于检测 EX 阶段 load 与 ID 阶段使用之间的 load-use hazard
    val idReadAddr1 = in UInt(5 bits)
    val idReadAddr2 = in UInt(5 bits)
    val idWriteAddr = in UInt(5 bits)
    val idResultSrc = in(ResultSrc())

    val rdMul = in UInt(5 bits)
    val rdDiv = in UInt(5 bits)
    val mulBusy = in Bool()
    val divBusy = in Bool()
    val regWriteMul = in Bool()
    val regWriteDiv = in Bool()

    val stall = out Bool()
    val flush = out Bool()
    val stallMulW = out Bool()
    val stallDivW = out Bool()
  }

  val exIsLoad = io.idEx.loadCtrl =/= 3
  val loadUseHazard = exIsLoad && (io.idEx.rd =/= 0) &&
    ((io.idEx.rd === io.idReadAddr1) || (io.idEx.rd === io.idReadAddr2))

  val mulDataHazard = io.mulBusy && (io.rdMul =/= 0) &&
    ((io.rdMul === io.idReadAddr1) || (io.rdMul === io.idReadAddr2) || (io.rdMul === io.idWriteAddr))
  val mulLocking = io.mulBusy && (io.idResultSrc === ResultSrc.mul)

  val divDataHazard = io.divBusy && (io.rdDiv =/= 0) &&
    ((io.rdDiv === io.idReadAddr1) || (io.rdDiv === io.idReadAddr2) || (io.rdDiv === io.idWriteAddr))
  val divLocking = io.divBusy && (io.idResultSrc === ResultSrc.div)

  io.stall := loadUseHazard || mulDataHazard || mulLocking || divDataHazard || divLocking
  io.flush := loadUseHazard || mulDataHazard || mulLocking || divDataHazard || divLocking

  io.stallDivW := False
  io.stallMulW := False
}
