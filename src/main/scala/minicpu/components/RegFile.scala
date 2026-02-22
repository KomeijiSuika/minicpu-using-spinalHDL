package minicpu.components

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig

class RegFile(config: CpuConfig) extends Component {
  // TODO: 32×32 寄存器堆（x0 硬连 0，2读1写）
  val io = new Bundle {
    val readAddr1 = in UInt(5 bits)
    val readAddr2 = in UInt(5 bits)
    val writeAddr = in UInt(5 bits)
    val writeData = in UInt(config.xlen bits)
    val writeEnable = in Bool()
    val readData1 = out UInt(config.xlen bits)
    val readData2 = out UInt(config.xlen bits)
  }
  
  val regFile = Mem(UInt(config.xlen bits), 32)
  
  io.readData1 := regFile(io.readAddr1)
  io.readData2 := regFile(io.readAddr2)
  // x0 寄存器硬连 0
  when(io.readAddr1 === 0) { io.readData1 := 0 }
  when(io.readAddr2 === 0) { io.readData2 := 0 }

  when(io.writeEnable && io.writeAddr =/= 0) {
    regFile(io.writeAddr) := io.writeData
  }

}
