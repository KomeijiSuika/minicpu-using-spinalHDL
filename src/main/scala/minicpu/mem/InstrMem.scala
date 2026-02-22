package minicpu.mem

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig

class InstrMem(config: CpuConfig) extends Component {

  val io = new Bundle {
    val addr = in UInt(config.xlen bits)
    val instr = out UInt(config.xlen bits)
  }

  val instrMem = Mem(UInt(config.xlen bits), 1024) // 1KB 指令存储器
  instrMem.simPublic() // 便于仿真观察
  io.instr := instrMem(io.addr >> 2) // 按字寻址，地址右移 2 位

}
