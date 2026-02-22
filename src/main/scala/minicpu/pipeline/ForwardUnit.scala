package minicpu.pipeline

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig

class ForwardUnit(config: CpuConfig) extends Component {
  val io = new Bundle {
    val idEx = in(idExReg(config))
    val exMem = in(exMemReg(config))
    val memWb = in(memWbReg(config))
    val forwardA = out UInt(2 bits) // 00: from regFile, 01: from EX/MEM, 10: from MEM/WB
    val forwardB = out UInt(2 bits)
  }

  io.forwardA := 0
  when(io.exMem.regWriteEnable && (io.exMem.rd =/= 0) && (io.exMem.rd === io.idEx.readAddr1)) {
    io.forwardA := 1 // EX/MEM 前递
  } elsewhen (io.memWb.regWriteEnable && (io.memWb.rd =/= 0) && (io.memWb.rd === io.idEx.readAddr1)) {
    io.forwardA := 2 // MEM/WB 前递
  }

  io.forwardB := 0
  when(io.exMem.regWriteEnable && (io.exMem.rd =/= 0) && (io.exMem.rd === io.idEx.readAddr2)) {
    io.forwardB := 1 // EX/MEM 前递
  } elsewhen (io.memWb.regWriteEnable && (io.memWb.rd =/= 0) && (io.memWb.rd === io.idEx.readAddr2)) {
    io.forwardB := 2 // MEM/WB 前递
  }
}
