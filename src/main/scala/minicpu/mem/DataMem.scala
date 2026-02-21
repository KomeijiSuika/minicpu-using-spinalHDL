package minicpu.mem

import spinal.core._
import spinal.lib._
import minicpu.CpuConfig
import minicpu.isa.Decode
import minicpu.isa.Rv32iEncoding._

class DataMem(config: CpuConfig) extends Component {
  val io = new Bundle {
    // in
    val addr = in UInt(config.xlen bits)
    val writeData = in UInt(config.xlen bits)
    val memWriteEnable = in Bool()
    val loadCtrl = in UInt(3 bits)
    val storeCtrl = in UInt(3 bits)
    // out
    val readData = out UInt(config.xlen bits)
  }
  val dataMem = Mem(Uint(config.xlen bits), 1024) 

  switch(io.loadCtrl) {
    is(Funct3Load.LB)  { io.readData := dataMem(io.addr >> 2)(7 downto 0).asSInt.resize(config.xlen) }   // LB
    is(Funct3Load.LH)  { io.readData := dataMem(io.addr >> 2)(15 downto 0).asSInt.resize(config.xlen) } // LH
    is(Funct3Load.LW)  { io.readData := dataMem(io.addr >> 2) }                                           // LW
    is(Funct3Load.LBU) { io.readData := dataMem(io.addr >> 2)(7 downto 0).asUInt.resize(config.xlen) }   // LBU
    is(Funct3Load.LHU) { io.readData := dataMem(io.addr >> 2)(15 downto 0).asUInt.resize(config.xlen) } // LHU
    default { io.readData := 0 }
  }

  when(io.memWriteEnable) {
    switch(io.storeCtrl) {
      is(Funct3Store.SB) { dataMem(io.addr >> 2)(7 downto 0) := io.writeData(7 downto 0) }   // SB
      is(Funct3Store.SH) { dataMem(io.addr >> 2)(15 downto 0) := io.writeData(15 downto 0) } // SH
      is(Funct3Store.SW) { dataMem(io.addr >> 2) := io.writeData }                             // SW
      default {}
    }
  }
}
