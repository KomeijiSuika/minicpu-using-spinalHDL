package minicpu.pipeline

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig

class HazardUnit(config: CpuConfig) extends Component {
  val io = new Bundle {
    val idEx = in(idExReg(config))
    val exMem = in(exMemReg(config))
    val memWb = in(memWbReg(config))

    // ID 阶段（当前 decode、下一拍要进入 EX 的指令）的源寄存器号
    // 用于检测 EX 阶段 load 与 ID 阶段使用之间的 load-use hazard
    val idReadAddr1 = in UInt(5 bits)
    val idReadAddr2 = in UInt(5 bits)

    val stall = out Bool()
    val flush = out Bool()
  }

  val exIsLoad = io.idEx.loadCtrl =/= 3
  val loadUseHazard = exIsLoad && (io.idEx.rd =/= 0) &&
    ((io.idEx.rd === io.idReadAddr1) || (io.idEx.rd === io.idReadAddr2))

  io.stall := loadUseHazard
  io.flush := loadUseHazard
}
