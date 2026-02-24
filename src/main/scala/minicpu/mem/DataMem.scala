package minicpu.mem

import spinal.core._
import spinal.lib._
import spinal.core.sim._
import minicpu.CpuConfig
import minicpu.isa.Rv32iEncoding._

class DataMem(config: CpuConfig) extends Component {
  // -----------------------------------------------------------------------------
  // Bank 设计说明（目的是与pipelined-rv32i项目中的 MMU 对齐，以便在之后验证自身的项目）
  // 1) 本模块按地址高 4 位（addr[31:28]）区分 bank：
  //    - 0x0: 指令区（INST）
  //    - 0x1: 内存映射寄存器区（MMRS）
  //    - 0x2: 显存区（VRAM）
  //    - 0x3: 数据区（DATA）
  // 2) DataMem 只负责 DATA bank（0x3），其它 bank 的访问在完整系统中应由对应模块处理。
  // 3) 这样做的原因：避免不同地址空间发生“别名写入/读取”，
  //    防止本应写到外设或指令区的访问误污染 dataMem。
  // -----------------------------------------------------------------------------
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

  // 地址高 4 位 bank 编码（与参考 MMU 一致）：
  // 0x0: 指令区，0x1: 内存映射寄存器，0x2: VRAM，0x3: 数据 RAM。
  // 当前 DataMem 仅建模数据 RAM bank（0x3）。
  val mmuBankInst = U(0, 4 bits)
  val mmuBankMmrs = U(1, 4 bits)
  val mmuBankVram = U(2, 4 bits)
  val mmuBankData = U(3, 4 bits)

  // 由 address[31:28] 解析 bank。
  // 目的：阻止其它 bank 的访问别名到 dataMem。
  // 例如：写低地址（如 0x00000078）在完整 MMU 中应落到指令/外设空间，
  // 不应误写到这里的 dataMem 影子区域。
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
  // 仅当地址落在 DATA bank 时返回有效 load 结果；
  // 非 DATA bank 返回 0，用于模拟“该从设备不处理此地址”。
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
    // Store 同样受 isDataBank 门控：
    // 本模块不应吞掉原本应写到 MMR/VRAM/INST 空间的请求。
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
