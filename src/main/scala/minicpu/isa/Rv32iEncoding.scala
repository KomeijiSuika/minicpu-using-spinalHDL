package minicpu.isa

import spinal.core._

object OpType {
  // RV32I Main Opcodes (7 bits: inst[6:0])
  val LOAD    = U(0x03, 7 bits) // Load: LB, LH, LW, LBU, LHU
  val IMMOP   = U(0x13, 7 bits) // I-type: ADDI, ANDI, ORI, XORI, SLTI, SLTIU, SLLI, SRLI, SRAI
  val AUIPC   = U(0x17, 7 bits) // Add Upper Immediate to PC
  val STORE   = U(0x23, 7 bits) // Store: SB, SH, SW
  val REGOP   = U(0x33, 7 bits) // R-type: ADD, SUB, AND, OR, XOR, SLL, SRL, SRA, SLT, SLTU
  val LUI     = U(0x37, 7 bits) // Load Upper Immediate
  val BRANCH  = U(0x63, 7 bits) // Branch: BEQ, BNE, BLT, BGE, BLTU, BGEU
  val JALR    = U(0x67, 7 bits) // Jump and Link Register
  val JAL     = U(0x6F, 7 bits) // Jump and Link
  val MISCMEM = U(0x0F, 7 bits) // FENCE, FENCE.I
  val SYSTEM  = U(0x73, 7 bits) // ECALL, EBREAK, CSR instructions
  val INVALID = U(0x7F, 7 bits) // Invalid opcode
}

object Funct3Load {
  // funct3 for LOAD instructions (bits 14:12)
  val LB  = U(0, 3 bits)  // Load Byte (signed)
  val LH  = U(1, 3 bits)  // Load Half-word (signed)
  val LW  = U(2, 3 bits)  // Load Word
  val LBU = U(4, 3 bits)  // Load Byte (unsigned)
  val LHU = U(5, 3 bits)  // Load Half-word (unsigned)
}

object Funct3Store {
  // funct3 for STORE instructions (bits 14:12)
  val SB = U(0, 3 bits)  // Store Byte
  val SH = U(1, 3 bits)  // Store Half-word
  val SW = U(2, 3 bits)  // Store Word
}

object Funct3IType {
  // funct3 for I-type and R-type ALU instructions
  val ADD_SUB = U(0, 3 bits)  // ADD (I) or SUB/ADD (R - depends on funct7)
  val SLL     = U(1, 3 bits)  // Shift Left Logical
  val SLT     = U(2, 3 bits)  // Set Less Than (signed)
  val SLTU    = U(3, 3 bits)  // Set Less Than Unsigned
  val XOR     = U(4, 3 bits)  // XOR
  val SRL_SRA = U(5, 3 bits)  // Shift Right (Logical/Arithmetic - depends on funct7)
  val OR      = U(6, 3 bits)  // OR
  val AND     = U(7, 3 bits)  // AND
}

object Funct3Branch {
  // funct3 for BRANCH instructions (bits 14:12)
  val BEQ  = U(0, 3 bits)  // Branch if Equal
  val BNE  = U(1, 3 bits)  // Branch if Not Equal
  val BLT  = U(4, 3 bits)  // Branch if Less Than (signed) - Note: non-sequential
  val BGE  = U(5, 3 bits)  // Branch if Greater or Equal (signed)
  val BLTU = U(6, 3 bits)  // Branch if Less Than Unsigned
  val BGEU = U(7, 3 bits)  // Branch if Greater or Equal Unsigned
}

object Funct7Type {
  // funct7 for R-type instructions (bits 31:25)
  val DEFAULT = U(0x00, 7 bits)  // ADD, SLL, SRL, AND, OR, XOR, SLT, SLTU
  val ALT     = U(0x20, 7 bits)  // SUB, SRA (0x20 = 7'b0100000)
}

object ImmType {
  // Immediate extension type (内部用于 ImmGen - 编码可任意）
  val I_TYPE = U(0, 3 bits)  // I-type: inst[31:20] sign-extended
  val S_TYPE = U(1, 3 bits)  // S-type: {inst[31:25], inst[11:7]}
  val B_TYPE = U(2, 3 bits)  // B-type: {inst[31], inst[7], inst[30:25], inst[11:8], 1'b0}
  val U_TYPE = U(3, 3 bits)  // U-type: {inst[31:12], 12'b0}
  val J_TYPE = U(4, 3 bits)  // J-type: {inst[31], inst[19:12], inst[20], inst[30:21], 1'b0}
}

object Rv32iExtractor {
  /**
   * 从指令提取 opcode (bits 6:0)
   */
  def getOpcode(instr: UInt): UInt = instr(6 downto 0)
  
  /**
   * 从指令提取 rd (bits 11:7) - 目标寄存器
   */
  def getRd(instr: UInt): UInt = instr(11 downto 7)
  
  /**
   * 从指令提取 funct3 (bits 14:12)
   */
  def getFunct3(instr: UInt): UInt = instr(14 downto 12)
  
  /**
   * 从指令提取 rs1 (bits 19:15) - 第一个源寄存器
   */
  def getRs1(instr: UInt): UInt = instr(19 downto 15)
  
  /**
   * 从指令提取 rs2 (bits 24:20) - 第二个源寄存器
   */
  def getRs2(instr: UInt): UInt = instr(24 downto 20)
  
  /**
   * 从指令提取 funct7 (bits 31:25)
   */
  def getFunct7(instr: UInt): UInt = instr(31 downto 25)
  
  /**
   * 从指令提取 I-type 立即数 (bits 31:20)，需要符号扩展
   */
  def getImm12I(instr: UInt): UInt = instr(31 downto 20).asSInt.resize(32).asUInt
  
  /**
   * 从指令提取 shamt (bits 24:20)，用于移位指令
   */
  def getShamt(instr: UInt): UInt = instr(24 downto 20)
  
  /**
   * 从指令提取 S-type 立即数: {inst[31:25], inst[11:7]}
   */
  def getImmS(instr: UInt): UInt = (instr(31 downto 25) ## instr(11 downto 7)).asSInt.resize(32).asUInt
  
  /**
   * 从指令提取 B-type 立即数: {inst[31], inst[7], inst[30:25], inst[11:8]}
   * 返回值的 bit 0 应该为 0（因为是 2 字节对齐）
   */
  def getImmB(instr: UInt): UInt = (instr(31) ## instr(7) ## instr(30 downto 25) ## instr(11 downto 8) ## U(0, 1 bits)).asSInt.resize(32).asUInt
  
  /**
   * 从指令提取 U-type 立即数: inst[31:12]
   */
  def getImmU(instr: UInt): UInt = (instr(31 downto 12) ## U(0, 12 bits)).asUInt
  
  /**
   * 从指令提取 J-type 立即数: {inst[31], inst[19:12], inst[20], inst[30:21]}
   * 返回值的 bit 0 应该为 0（因为是 2 字节对齐）
   */
  def getImmJ(instr: UInt): UInt = (instr(31) ## instr(19 downto 12) ## instr(20) ## instr(30 downto 21) ## U(0, 1 bits)).asSInt.resize(32).asUInt
  
  /**
   * 判断是否为 R-type 指令
   */
  def isRType(opcode: UInt): Bool = opcode === OpType.REGOP
  
  /**
   * 判断是否为 I-type 指令
   */
  def isIType(opcode: UInt): Bool = opcode === OpType.IMMOP || opcode === OpType.JALR || opcode === OpType.LOAD
  
  /**
   * 判断是否为 Load 指令
   */
  def isLoad(opcode: UInt): Bool = opcode === OpType.LOAD
  
  /**
   * 判断是否为 Store 指令
   */
  def isStore(opcode: UInt): Bool = opcode === OpType.STORE
  
  /**
   * 判断是否为 Branch 指令
   */
  def isBranch(opcode: UInt): Bool = opcode === OpType.BRANCH
  
  /**
   * 判断是否为 Jump 指令（JAL 或 JALR）
   */
  def isJump(opcode: UInt): Bool = opcode === OpType.JAL || opcode === OpType.JALR
}
