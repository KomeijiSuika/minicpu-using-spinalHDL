package minicpu.mem

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig
import minicpu.isa.Decode

class DataMem(config: CpuConfig) extends Component {
  val io = new Bundle {
    // in
    val addr = in UInt(config.xlen bits)
    val writeData = in UInt(config.xlen bits)
    val memWriteEnable = in Bool()

    // out
    val readData = out UInt(config.xlen bits)
  }
}
