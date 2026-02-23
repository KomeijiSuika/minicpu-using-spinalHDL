package minicpu.components

import spinal.core._
import spinal.lib._
import spinal.core.sim._
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
    
    // debug 端口（单独一个读端口，便于仿真观察寄存器状态）
    val dbg_readAddr = in UInt(5 bits)
    val dbg_readData = out UInt(config.xlen bits)
  }
  
  val regFile = Mem(UInt(config.xlen bits), 32)
  regFile.simPublic() // allow testbench to read/write registers via spinal.core.sim.getBigInt/setBigInt
  
  io.readData1 := regFile(io.readAddr1)
  io.readData2 := regFile(io.readAddr2)

  io.dbg_readData := regFile(io.dbg_readAddr)
  // x0 寄存器硬连 0
  when(io.readAddr1 === 0) { io.readData1 := 0 }
  when(io.readAddr2 === 0) { io.readData2 := 0 }
  when(io.dbg_readAddr === 0) { io.dbg_readData := 0 }

  when(io.writeEnable && io.writeAddr =/= 0) {
    regFile(io.writeAddr) := io.writeData
  }

}
