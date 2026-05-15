package minicpu.validation

object ValidationEncoding {
  private def word(value: Long): Long = value & 0xffffffffL

  private def rType(funct7: Int, rs2: Int, rs1: Int, funct3: Int, rd: Int, opcode: Int = 0x33): Long = {
    word(
      ((funct7 & 0x7fL) << 25) |
        ((rs2 & 0x1fL) << 20) |
        ((rs1 & 0x1fL) << 15) |
        ((funct3 & 0x7L) << 12) |
        ((rd & 0x1fL) << 7) |
        (opcode & 0x7fL)
    )
  }

  private def iType(imm: Int, rs1: Int, funct3: Int, rd: Int, opcode: Int): Long = {
    word(
      (((imm & 0xfffL) << 20)) |
        ((rs1 & 0x1fL) << 15) |
        ((funct3 & 0x7L) << 12) |
        ((rd & 0x1fL) << 7) |
        (opcode & 0x7fL)
    )
  }

  private def sType(imm: Int, rs2: Int, rs1: Int, funct3: Int, opcode: Int = 0x23): Long = {
    val value = imm & 0xfff
    word(
      (((value >> 5) & 0x7fL) << 25) |
        ((rs2 & 0x1fL) << 20) |
        ((rs1 & 0x1fL) << 15) |
        ((funct3 & 0x7L) << 12) |
        (((value >> 0) & 0x1fL) << 7) |
        (opcode & 0x7fL)
    )
  }

  private def bType(imm: Int, rs2: Int, rs1: Int, funct3: Int, opcode: Int = 0x63): Long = {
    val value = imm & 0x1fff
    word(
      (((value >> 12) & 0x1L) << 31) |
        (((value >> 5) & 0x3fL) << 25) |
        ((rs2 & 0x1fL) << 20) |
        ((rs1 & 0x1fL) << 15) |
        ((funct3 & 0x7L) << 12) |
        (((value >> 1) & 0xfL) << 8) |
        (((value >> 11) & 0x1L) << 7) |
        (opcode & 0x7fL)
    )
  }

  private def uType(imm20: Int, rd: Int, opcode: Int): Long = {
    word(
      (((imm20 & 0xfffffL) << 12)) |
        ((rd & 0x1fL) << 7) |
        (opcode & 0x7fL)
    )
  }

  private def jType(imm: Int, rd: Int, opcode: Int = 0x6f): Long = {
    val value = imm & 0x1fffff
    word(
      (((value >> 20) & 0x1L) << 31) |
        (((value >> 1) & 0x3ffL) << 21) |
        (((value >> 11) & 0x1L) << 20) |
        (((value >> 12) & 0xffL) << 12) |
        ((rd & 0x1fL) << 7) |
        (opcode & 0x7fL)
    )
  }

  def nop: Long = addi(0, 0, 0)

  def addi(rd: Int, rs1: Int, imm: Int): Long = iType(imm, rs1, 0x0, rd, 0x13)
  def andi(rd: Int, rs1: Int, imm: Int): Long = iType(imm, rs1, 0x7, rd, 0x13)
  def ori(rd: Int, rs1: Int, imm: Int): Long = iType(imm, rs1, 0x6, rd, 0x13)
  def xori(rd: Int, rs1: Int, imm: Int): Long = iType(imm, rs1, 0x4, rd, 0x13)
  def lbu(rd: Int, rs1: Int, imm: Int): Long = iType(imm, rs1, 0x4, rd, 0x03)
  def lhu(rd: Int, rs1: Int, imm: Int): Long = iType(imm, rs1, 0x5, rd, 0x03)
  def lw(rd: Int, rs1: Int, imm: Int): Long = iType(imm, rs1, 0x2, rd, 0x03)
  def jalr(rd: Int, rs1: Int, imm: Int): Long = iType(imm, rs1, 0x0, rd, 0x67)

  def add(rd: Int, rs1: Int, rs2: Int): Long = rType(0x00, rs2, rs1, 0x0, rd)
  def sub(rd: Int, rs1: Int, rs2: Int): Long = rType(0x20, rs2, rs1, 0x0, rd)
  def sll(rd: Int, rs1: Int, rs2: Int): Long = rType(0x00, rs2, rs1, 0x1, rd)
  def slt(rd: Int, rs1: Int, rs2: Int): Long = rType(0x00, rs2, rs1, 0x2, rd)
  def sltu(rd: Int, rs1: Int, rs2: Int): Long = rType(0x00, rs2, rs1, 0x3, rd)
  def mul(rd: Int, rs1: Int, rs2: Int): Long = rType(0x01, rs2, rs1, 0x0, rd)
  def mulh(rd: Int, rs1: Int, rs2: Int): Long = rType(0x01, rs2, rs1, 0x1, rd)
  def mulhsu(rd: Int, rs1: Int, rs2: Int): Long = rType(0x01, rs2, rs1, 0x2, rd)
  def mulhu(rd: Int, rs1: Int, rs2: Int): Long = rType(0x01, rs2, rs1, 0x3, rd)
  def div(rd: Int, rs1: Int, rs2: Int): Long = rType(0x01, rs2, rs1, 0x4, rd)
  def divu(rd: Int, rs1: Int, rs2: Int): Long = rType(0x01, rs2, rs1, 0x5, rd)
  def rem(rd: Int, rs1: Int, rs2: Int): Long = rType(0x01, rs2, rs1, 0x6, rd)
  def remu(rd: Int, rs1: Int, rs2: Int): Long = rType(0x01, rs2, rs1, 0x7, rd)

  def sb(rs2: Int, rs1: Int, imm: Int): Long = sType(imm, rs2, rs1, 0x0)
  def sh(rs2: Int, rs1: Int, imm: Int): Long = sType(imm, rs2, rs1, 0x1)
  def sw(rs2: Int, rs1: Int, imm: Int): Long = sType(imm, rs2, rs1, 0x2)

  def beq(rs1: Int, rs2: Int, imm: Int): Long = bType(imm, rs2, rs1, 0x0)
  def bne(rs1: Int, rs2: Int, imm: Int): Long = bType(imm, rs2, rs1, 0x1)

  def lui(rd: Int, imm20: Int): Long = uType(imm20, rd, 0x37)
  def auipc(rd: Int, imm20: Int): Long = uType(imm20, rd, 0x17)

  def jal(rd: Int, imm: Int): Long = jType(imm, rd)
}
