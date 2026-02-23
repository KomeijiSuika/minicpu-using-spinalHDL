package minicpu.mem

import spinal.core._
import spinal.lib._
import spinal.core.sim._
import minicpu.CpuConfig
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

    // debug port
    val dbgAddr = in UInt(config.xlen bits)
    val dbgWriteEnable = in Bool()
    val dbgWriteData = in UInt(config.xlen bits)
    val dbgReadData = out UInt(config.xlen bits)
  }
  val dataMem = Mem(UInt(config.xlen bits), 1024)
  dataMem.simPublic() // allow testbench to dump full memory contents

  // Read helpers (byte/half selection inside a 32-bit word)
  val wordAddr = (io.addr >> 2).resized
  val word     = dataMem(wordAddr)
  val dbgWordAddr = (io.dbgAddr >> 2).resized
  io.dbgReadData := dataMem(dbgWordAddr)

  switch(io.loadCtrl) {
    is(Funct3Load.LB)  { io.readData := word(7 downto 0).asSInt.resize(config.xlen).asUInt }   // LB
    is(Funct3Load.LH)  { io.readData := word(15 downto 0).asSInt.resize(config.xlen).asUInt }  // LH
    is(Funct3Load.LW)  { io.readData := word }                                               // LW
    is(Funct3Load.LBU) { io.readData := word(7 downto 0).resized }                 // LBU
    is(Funct3Load.LHU) { io.readData := word(15 downto 0).resized }                // LHU
    default { io.readData := 0 }
  }

  when(io.dbgWriteEnable) {
    dataMem(dbgWordAddr) := io.dbgWriteData
  } elsewhen(io.memWriteEnable) {
    switch(io.storeCtrl) {
      is(Funct3Store.SB) { dataMem(wordAddr) := io.writeData(7 downto 0).resize(config.xlen) }   // SB
      is(Funct3Store.SH) { dataMem(wordAddr) := io.writeData(15 downto 0).resize(config.xlen) } // SH
      is(Funct3Store.SW) { dataMem(wordAddr) := io.writeData }                             // SW
      default {}
    }
  }
}
