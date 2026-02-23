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
  val wordAddr   = (io.addr >> 2).resized
  val word       = dataMem(wordAddr)
  val byteOffset = io.addr(1 downto 0)
  val shiftBits  = (byteOffset ## U(0, 3 bits)).asUInt.resized
  val shiftedWord = (word |>> shiftBits).resized

  val lbVal  = shiftedWord(7 downto 0).asSInt.resize(config.xlen).asUInt
  val lhVal  = shiftedWord(15 downto 0).asSInt.resize(config.xlen).asUInt
  val lbuVal = shiftedWord(7 downto 0).resized
  val lhuVal = shiftedWord(15 downto 0).resized

  val halfAligned = io.addr(0) === False
  val wordAligned = io.addr(1 downto 0) === 0

  val dbgWordAddr = (io.dbgAddr >> 2).resized
  io.dbgReadData := dataMem(dbgWordAddr)

  switch(io.loadCtrl) {
    is(Funct3Load.LB)  { io.readData := lbVal }                            // LB
    is(Funct3Load.LH)  { io.readData := Mux(halfAligned, lhVal, U(0)) }    // LH
    is(Funct3Load.LW)  { io.readData := Mux(wordAligned, word, U(0)) }     // LW
    is(Funct3Load.LBU) { io.readData := lbuVal }                           // LBU
    is(Funct3Load.LHU) { io.readData := Mux(halfAligned, lhuVal, U(0)) }   // LHU
    default { io.readData := 0 }
  }

  when(io.dbgWriteEnable) {
    dataMem(dbgWordAddr) := io.dbgWriteData
  } elsewhen(io.memWriteEnable) {
    val byteMask = (U(0xFF, config.xlen bits) |<< shiftBits).resized
    val halfMask = (U(0xFFFF, config.xlen bits) |<< shiftBits).resized
    val byteData = (io.writeData(7 downto 0).resize(config.xlen) |<< shiftBits).resized
    val halfData = (io.writeData(15 downto 0).resize(config.xlen) |<< shiftBits).resized

    switch(io.storeCtrl) {
      is(Funct3Store.SB) {
        dataMem(wordAddr) := (word & ~byteMask) | (byteData & byteMask)
      } // SB
      is(Funct3Store.SH) {
        when(halfAligned) {
          dataMem(wordAddr) := (word & ~halfMask) | (halfData & halfMask)
        }
      } // SH
      is(Funct3Store.SW) {
        when(wordAligned) {
          dataMem(wordAddr) := io.writeData
        }
      } // SW
      default {}
    }
  }
}
