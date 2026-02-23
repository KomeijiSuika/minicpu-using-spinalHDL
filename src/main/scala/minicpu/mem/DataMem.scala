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

  // Top-4-bit bank encoding, aligned with the reference MMU map:
  // 0x0: instruction, 0x1: memory-mapped registers, 0x2: VRAM, 0x3: data RAM.
  // This local DataMem only models the data RAM bank (0x3).
  val mmuBankInst = U(0, 4 bits)
  val mmuBankMmrs = U(1, 4 bits)
  val mmuBankVram = U(2, 4 bits)
  val mmuBankData = U(3, 4 bits)

  // Decode bank from address[31:28].
  // Purpose: avoid aliasing writes/reads from other banks into dataMem.
  // Example bug avoided: a store to low address (e.g. 0x00000078) should target
  // instruction/peripheral space in full MMU, not corrupt data RAM shadow here.
  val addrBank = io.addr(31 downto 28)
  val isDataBank = addrBank === mmuBankData

  // Read helpers (byte/half selection inside a 32-bit word)
  val wordAddr = (io.addr >> 2).resized
  val word     = dataMem(wordAddr)
  val byteOffset = io.addr(1 downto 0)
  val shiftBits = (byteOffset ## U(0, 3 bits)).asUInt.resized
  val shiftedWord = (word |>> shiftBits).resized

  val lbVal = shiftedWord(7 downto 0).asSInt.resize(config.xlen).asUInt
  val lhVal = shiftedWord(15 downto 0).asSInt.resize(config.xlen).asUInt
  val lbuVal = shiftedWord(7 downto 0).resized
  val lhuVal = shiftedWord(15 downto 0).resized

  val dbgWordAddr = (io.dbgAddr >> 2).resized
  io.dbgReadData := dataMem(dbgWordAddr)

  io.readData := 0
  // Loads only return valid data when current address points to DATA bank.
  // For non-data banks, return 0 to mimic "not handled by this slave" behavior.
  when(isDataBank) {
    switch(io.loadCtrl) {
      is(Funct3Load.LB)  { io.readData := lbVal }                 // LB
      is(Funct3Load.LH)  { io.readData := lhVal }                 // LH
      is(Funct3Load.LW)  { io.readData := word }                  // LW
      is(Funct3Load.LBU) { io.readData := lbuVal }                // LBU
      is(Funct3Load.LHU) { io.readData := lhuVal }                // LHU
      default { io.readData := 0 }
    }
  }

  when(io.dbgWriteEnable) {
    dataMem(dbgWordAddr) := io.dbgWriteData
  } elsewhen(io.memWriteEnable && isDataBank) {
    // Stores are gated by isDataBank for the same reason as loads:
    // this module should not consume writes intended for MMR/VRAM/INST space.
    val byteMask = (U(0xFF, config.xlen bits) |<< shiftBits).resized
    val halfMask = (U(0xFFFF, config.xlen bits) |<< shiftBits).resized
    val byteData = (io.writeData(7 downto 0).resize(config.xlen) |<< shiftBits).resized
    val halfData = (io.writeData(15 downto 0).resize(config.xlen) |<< shiftBits).resized

    switch(io.storeCtrl) {
      is(Funct3Store.SB) { dataMem(wordAddr) := (word & ~byteMask) | (byteData & byteMask) }   // SB
      is(Funct3Store.SH) { dataMem(wordAddr) := (word & ~halfMask) | (halfData & halfMask) } // SH
      is(Funct3Store.SW) { dataMem(wordAddr) := io.writeData }                             // SW
      default {}
    }
  }
}
