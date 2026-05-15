package minicpu.validation

import ValidationEncoding._

object GoldenMath {
  private val Mask32 = BigInt("ffffffff", 16)
  private val SignBit = BigInt(1) << 31
  private val WideMask = (BigInt(1) << 64) - 1

  def u32(value: BigInt): BigInt = value & Mask32

  def s32(value: BigInt): BigInt = {
    val masked = u32(value)
    if ((masked & SignBit) != 0) masked - (BigInt(1) << 32) else masked
  }

  private def mulWideSigned(a: BigInt, b: BigInt): BigInt = {
    (s32(a) * s32(b)) & WideMask
  }

  private def mulWideMixed(a: BigInt, b: BigInt): BigInt = {
    (s32(a) * u32(b)) & WideMask
  }

  private def mulWideUnsigned(a: BigInt, b: BigInt): BigInt = {
    (u32(a) * u32(b)) & WideMask
  }

  def mul(a: BigInt, b: BigInt): BigInt = u32(s32(a) * s32(b))
  def mulh(a: BigInt, b: BigInt): BigInt = u32(mulWideSigned(a, b) >> 32)
  def mulhsu(a: BigInt, b: BigInt): BigInt = u32(mulWideMixed(a, b) >> 32)
  def mulhu(a: BigInt, b: BigInt): BigInt = u32(mulWideUnsigned(a, b) >> 32)
}

object GoldenRV32I {
  val content = ValidationSuite(
    key = "TestRV32I",
    title = "RV32I functional validation suite",
    cases = Seq(
      ValidationCase(
        name = "alu_basic",
        program = Seq(
          addi(1, 0, 10),
          addi(2, 0, 3),
          add(3, 1, 2),
          sub(4, 1, 2),
          sll(5, 2, 2),
          ori(6, 1, 5),
          andi(7, 6, 6),
          xori(8, 7, 3),
          slt(9, 2, 1),
          sltu(10, 2, 1)
        ),
        cycleLimit = 50,
        expectedRegs = Map(
          3 -> BigInt(13),
          4 -> BigInt(7),
          5 -> BigInt(24),
          6 -> BigInt(15),
          7 -> BigInt(6),
          8 -> BigInt(5),
          9 -> BigInt(1),
          10 -> BigInt(1)
        )
      ),
      ValidationCase(
        name = "load_store_data_bank",
        program = Seq(
          lui(1, 0x30000),
          addi(2, 0, 20),
          sw(2, 1, 0),
          lw(3, 1, 0),
          addi(4, 0, 127),
          sb(4, 1, 4),
          lbu(5, 1, 4),
          addi(6, 0, 18),
          sh(6, 1, 8),
          lhu(7, 1, 8)
        ),
        cycleLimit = 70,
        expectedRegs = Map(
          1 -> BigInt("30000000", 16),
          3 -> BigInt(20),
          5 -> BigInt(127),
          7 -> BigInt(18)
        ),
        expectedMemWords = Map(
          0 -> BigInt(20),
          1 -> BigInt(127),
          2 -> BigInt(18)
        )
      ),
      ValidationCase(
        name = "branch_and_jal",
        program = Seq(
          addi(1, 0, 5),
          addi(2, 0, 5),
          beq(1, 2, 8),
          addi(3, 0, 1),
          jal(4, 8),
          addi(3, 0, 2),
          addi(3, 0, 3)
        ),
        cycleLimit = 60,
        expectedRegs = Map(
          3 -> BigInt(3),
          4 -> BigInt(20)
        )
      ),
      ValidationCase(
        name = "jalr_path",
        program = Seq(
          addi(5, 0, 16),
          jalr(6, 5, 0),
          addi(7, 0, 1),
          nop,
          addi(7, 0, 2)
        ),
        cycleLimit = 50,
        expectedRegs = Map(
          6 -> BigInt(8),
          7 -> BigInt(2)
        )
      ),
      ValidationCase(
        name = "upper_immediates",
        program = Seq(
          nop,
          auipc(2, 0x1),
          lui(3, 0x12345),
          lui(4, 0xfffff)
        ),
        cycleLimit = 40,
        expectedRegs = Map(
          2 -> BigInt("00001004", 16),
          3 -> BigInt("12345000", 16),
          4 -> BigInt("fffff000", 16)
        )
      )
    )
  )
}

object GoldenRV32M {
  private val neg91 = BigInt(-91)
  private val pos15 = BigInt(15)

  val content = ValidationSuite(
    key = "TestRV32M",
    title = "RV32M functional validation suite",
    cases = Seq(
      ValidationCase(
        name = "mul_div_rem_basic",
        program = Seq(
          addi(1, 0, 13),
          addi(2, 0, 5),
          mul(3, 1, 2),
          div(4, 1, 2),
          rem(5, 1, 2)
        ),
        cycleLimit = 120,
        expectedRegs = Map(
          3 -> BigInt(65),
          4 -> BigInt(2),
          5 -> BigInt(3)
        )
      ),
      ValidationCase(
        name = "mul_high_variants",
        program = Seq(
          addi(1, 0, -91),
          addi(2, 0, 15),
          mul(3, 1, 2),
          mulh(4, 1, 2),
          mulhsu(5, 1, 2),
          mulhu(6, 1, 2)
        ),
        cycleLimit = 140,
        expectedRegs = Map(
          3 -> GoldenMath.mul(neg91, pos15),
          4 -> GoldenMath.mulh(neg91, pos15),
          5 -> GoldenMath.mulhsu(neg91, pos15),
          6 -> GoldenMath.mulhu(neg91, pos15)
        )
      ),
      ValidationCase(
        name = "signed_div_and_zero_corner",
        program = Seq(
          addi(1, 0, -13),
          addi(2, 0, 5),
          div(6, 1, 2),
          rem(7, 1, 2),
          div(9, 1, 0),
          rem(10, 1, 0)
        ),
        cycleLimit = 160,
        expectedRegs = Map(
          6 -> BigInt("fffffffe", 16),
          7 -> BigInt("fffffffd", 16),
          9 -> BigInt("ffffffff", 16),
          10 -> BigInt("fffffff3", 16)
        )
      ),
      ValidationCase(
        name = "dependent_chain_hazard",
        program = Seq(
          addi(1, 0, 13),
          addi(2, 0, 5),
          mul(3, 1, 2),
          addi(4, 3, 1),
          div(5, 3, 2),
          rem(6, 3, 2)
        ),
        cycleLimit = 140,
        expectedRegs = Map(
          3 -> BigInt(65),
          4 -> BigInt(66),
          5 -> BigInt(13),
          6 -> BigInt(0)
        )
      ),
      ValidationCase(
        name = "waw_ordering",
        program = Seq(
          addi(1, 0, 8),
          addi(2, 0, 2),
          div(3, 1, 2),
          mul(3, 1, 2)
        ),
        cycleLimit = 140,
        expectedRegs = Map(
          3 -> BigInt(16)
        )
      )
    )
  )
}
