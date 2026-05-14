package minicpu.mdu

import minicpu.CpuConfig
import spinal.core._
import spinal.lib._

object MulOp extends SpinalEnum {
  val mul = newElement("mul")
  val mulh = newElement("mulh")
  val mulhsu = newElement("mulhsu")
  val mulhu = newElement("mulhu")
}

object DivOp extends SpinalEnum {
  val div = newElement("div")
  val divu = newElement("divu")
  val rem = newElement("rem")
  val remu = newElement("remu")
}

object MduOp extends SpinalEnum {
  val none = newElement("none")
  val mul = newElement("mul")
  val mulh = newElement("mulh")
  val mulhsu = newElement("mulhsu")
  val mulhu = newElement("mulhu")
  val div = newElement("div")
  val divu = newElement("divu")
  val rem = newElement("rem")
  val remu = newElement("remu")

  def isMul(op: MduOp.C): Bool = {
    op === mul || op === mulh || op === mulhsu || op === mulhu
  }

  def isDiv(op: MduOp.C): Bool = {
    op === div || op === divu || op === rem || op === remu
  }

  def toMulOp(op: MduOp.C): MulOp.C = {
    val mapped = MulOp()
    mapped := MulOp.mul
    when(op === mulh) {
      mapped := MulOp.mulh
    } elsewhen (op === mulhsu) {
      mapped := MulOp.mulhsu
    } elsewhen (op === mulhu) {
      mapped := MulOp.mulhu
    }
    mapped
  }

  def toDivOp(op: MduOp.C): DivOp.C = {
    val mapped = DivOp()
    mapped := DivOp.div
    when(op === divu) {
      mapped := DivOp.divu
    } elsewhen (op === rem) {
      mapped := DivOp.rem
    } elsewhen (op === remu) {
      mapped := DivOp.remu
    }
    mapped
  }
}

object BitMath {
  def leadingZeros(value: Bits): UInt = {
    val width = value.getWidth
    val result = UInt(log2Up(width + 1) bits)
    result := width

    var chain = when(value(width - 1)) {
      result := 0
    }
    for (zeros <- 1 until width) {
      chain = chain.elsewhen(value(width - 1 - zeros)) {
        result := zeros
      }
    }

    result
  }
}

object BoothMulSupport {
  def signedA(op: MulOp.C): Bool = {
    op === MulOp.mul || op === MulOp.mulh || op === MulOp.mulhsu
  }

  def signedB(op: MulOp.C): Bool = {
    op === MulOp.mul || op === MulOp.mulh
  }

  def selectResult(op: MulOp.C, product: Bits, dataWidth: Int): Bits = {
    val result = Bits(dataWidth bits)
    result := product(dataWidth - 1 downto 0)
    when(op === MulOp.mulh || op === MulOp.mulhu || op === MulOp.mulhsu) {
      result := product(2 * dataWidth - 1 downto dataWidth)
    }
    result
  }
}

case class Multiplier(config: CpuConfig) extends Component {
  private val dataWidth = config.xlen
  private val productWidth = 2 * dataWidth
  private val internalWidth = productWidth + 4
  private val groupCount = dataWidth / 2 + 1
  private val countWidth = log2Up(groupCount + 1)

  val io = new Bundle {
    val start = in Bool()
    val srca = in Bits(dataWidth bits)
    val srcb = in Bits(dataWidth bits)
    val mulOp = in(MulOp())
    val busy = out Bool()
    val done = out Bool()
    val result = out Bits(dataWidth bits)
  }

  val busyReg = RegInit(False)
  val doneReg = RegInit(False)
  val opReg = Reg(MulOp()).init(MulOp.mul)
  val productReg = Reg(Bits(productWidth bits)).init(0)

  val accReg = Reg(SInt(internalWidth bits)).init(0)
  val shiftedMultiplicandReg = Reg(SInt(internalWidth bits)).init(0)
  val multiplierWindowReg = Reg(Bits(dataWidth + 3 bits)).init(0)
  val multiplierSignReg = RegInit(False)
  val groupReg = Reg(UInt(countWidth bits)).init(0)

  io.busy := busyReg
  io.done := doneReg
  io.result := BoothMulSupport.selectResult(opReg, productReg, dataWidth)

  doneReg := False

  val boothCode = multiplierWindowReg(2 downto 0)
  val partial = SInt(internalWidth bits)
  partial := 0
  when(boothCode === B"3'b001" || boothCode === B"3'b010") {
    partial := shiftedMultiplicandReg
  } elsewhen (boothCode === B"3'b011") {
    partial := (shiftedMultiplicandReg |<< 1).resize(internalWidth)
  } elsewhen (boothCode === B"3'b100") {
    partial := -((shiftedMultiplicandReg |<< 1).resize(internalWidth))
  } elsewhen (boothCode === B"3'b101" || boothCode === B"3'b110") {
    partial := -shiftedMultiplicandReg
  }

  val accNext = (accReg + partial).resize(internalWidth)

  when(io.start && !busyReg) {
    val signedA = BoothMulSupport.signedA(io.mulOp)
    val signedB = BoothMulSupport.signedB(io.mulOp)
    val multiplicand = SInt(internalWidth bits)

    multiplicand := io.srca.asUInt.resize(internalWidth).asSInt
    when(signedA) {
      multiplicand := io.srca.asSInt.resize(internalWidth)
    }

    opReg := io.mulOp
    accReg := 0
    shiftedMultiplicandReg := multiplicand
    multiplierWindowReg := ((signedB && io.srcb.msb) ## (signedB && io.srcb.msb) ## io.srcb ## False)
    multiplierSignReg := signedB && io.srcb.msb
    groupReg := 0
    busyReg := True
  } elsewhen (busyReg) {
    accReg := accNext
    shiftedMultiplicandReg := (shiftedMultiplicandReg |<< 2).resize(internalWidth)
    multiplierWindowReg := (multiplierSignReg ## multiplierSignReg ## multiplierWindowReg(dataWidth + 2 downto 2))

    when(groupReg === U(groupCount - 1, countWidth bits)) {
      productReg := accNext(productWidth - 1 downto 0).asBits
      busyReg := False
      doneReg := True
    } otherwise {
      groupReg := groupReg + 1
    }
  }
}

object DividerSupport {
  def twosComplement(value: UInt): UInt = ~value + U(1, value.getWidth bits)

  def magnitude(value: Bits, signedOp: Bool): UInt = {
    val negative = signedOp && value.msb
    Mux(negative, twosComplement(value.asUInt), value.asUInt)
  }

  def applySign(value: UInt, negative: Bool): Bits = {
    Mux(negative, twosComplement(value), value).asBits
  }

  def isMinSigned(value: Bits): Bool = {
    value.msb && !value(value.getWidth - 2 downto 0).orR
  }

  def isMinusOne(value: Bits): Bool = {
    value.andR
  }
}

object SrtQds {
  val RemBits = 8
  val DivBits = 5

  private val divBase = 16
  private val posOneLower = Seq(6, 6, 7, 7, 7, 8, 8, 8, 9, 9, 9, 10, 10, 10, 11, 11)
  private val posTwoLower = Seq(23, 24, 26, 27, 28, 30, 31, 32, 34, 35, 36, 38, 39, 40, 42, 43)
  private val zeroLower = Seq(-10, -11, -12, -12, -13, -14, -14, -15, -16, -16, -17, -18, -18, -19, -20, -20)
  private val negOneLower = Seq(-26, -28, -30, -31, -33, -35, -36, -38, -40, -41, -43, -45, -46, -48, -50, -51)
  private val firstOneLower = Seq(6, 6, 7, 7, 7, 8, 8, 8, 9, 9, 9, 10, 10, 10, 11, 11)

  def selectDigit(remTop: SInt, divTop: UInt, firstDigit: Bool): SInt = {
    val digit = SInt(3 bits)
    digit := 0

    when(firstDigit) {
      switch(divTop) {
        for (index <- 0 until 16) {
          is(U(index + divBase, DivBits bits)) {
            when(remTop >= S(firstOneLower(index), RemBits bits)) {
              digit := 1
            }
          }
        }
      }
    } otherwise {
      switch(divTop) {
        for (index <- 0 until 16) {
          is(U(index + divBase, DivBits bits)) {
            when(remTop >= S(posTwoLower(index), RemBits bits)) {
              digit := 2
            } elsewhen (remTop >= S(posOneLower(index), RemBits bits)) {
              digit := 1
            } elsewhen (remTop >= S(zeroLower(index), RemBits bits)) {
              digit := 0
            } elsewhen (remTop >= S(negOneLower(index), RemBits bits)) {
              digit := -1
            } otherwise {
              digit := -2
            }
          }
        }
      }
    }

    digit
  }
}

case class Divider(config: CpuConfig) extends Component {
  private val dataWidth = config.xlen
  private val remWidth = 2 * dataWidth + 4
  private val qAccWidth = dataWidth + 4
  private val countWidth = log2Up(dataWidth + 2)
  private val qdsNormWidth = remWidth + 3
  private val qdsShiftWidth = log2Up(qdsNormWidth + 1)

  val io = new Bundle {
    val start = in Bool()
    val srca = in Bits(dataWidth bits)
    val srcb = in Bits(dataWidth bits)
    val divOp = in(DivOp())
    val busy = out Bool()
    val done = out Bool()
    val result = out Bits(dataWidth bits)
  }

  val busyReg = RegInit(False)
  val doneReg = RegInit(False)
  val quotientReg = Reg(Bits(dataWidth bits)).init(0)
  val remainderRegOut = Reg(Bits(dataWidth bits)).init(0)
  val isRemReg = RegInit(False)
  val finalizeReg = RegInit(False)

  val divisorReg = Reg(UInt(dataWidth bits)).init(0)
  val remainderReg = Reg(SInt(remWidth bits)).init(0)
  val scaledDivisorReg = Reg(UInt(remWidth bits)).init(0)
  val qpReg = Reg(UInt(qAccWidth bits)).init(0)
  val qnReg = Reg(UInt(qAccWidth bits)).init(0)
  val posReg = Reg(UInt(countWidth bits)).init(0)
  val firstDigitReg = RegInit(False)
  val quotientNegReg = RegInit(False)
  val remainderNegReg = RegInit(False)
  val rawQuotientReg = Reg(SInt(qAccWidth + 1 bits)).init(0)
  val finalRemainderReg = Reg(SInt(qdsNormWidth bits)).init(0)

  io.busy := busyReg || finalizeReg
  io.done := doneReg
  io.result := Mux(isRemReg, remainderRegOut, quotientReg)

  doneReg := False

  val signed = io.divOp === DivOp.div || io.divOp === DivOp.rem
  val isRem = io.divOp === DivOp.rem || io.divOp === DivOp.remu

  val dividendNeg = signed && io.srca.msb
  val dividendMag = DividerSupport.magnitude(io.srca, signed)
  val divisorMag = DividerSupport.magnitude(io.srcb, signed)
  val quotientNeg = signed && (dividendNeg ^ io.srcb.msb)
  val remainderNeg = signed && dividendNeg

  when(io.start && !busyReg && !finalizeReg) {
    isRemReg := isRem

    when(io.srcb === 0) {
      quotientReg := B((BigInt(1) << dataWidth) - 1, dataWidth bits)
      remainderRegOut := io.srca
      doneReg := True
    } elsewhen (signed && DividerSupport.isMinSigned(io.srca) && DividerSupport.isMinusOne(io.srcb)) {
      quotientReg := io.srca
      remainderRegOut := B(0, dataWidth bits)
      doneReg := True
    } elsewhen (dividendMag < divisorMag) {
      quotientReg := B(0, dataWidth bits)
      remainderRegOut := DividerSupport.applySign(dividendMag, remainderNeg)
      doneReg := True
    } otherwise {
      val qBits = UInt(countWidth bits)
      val posStart = UInt(countWidth bits)
      val shiftAmount = UInt((countWidth + 1) bits)

      qBits := (
        BitMath.leadingZeros(divisorMag.asBits).resize(countWidth) -
          BitMath.leadingZeros(dividendMag.asBits).resize(countWidth) +
          U(1, countWidth bits)
      ).resized

      posStart := ((qBits + U(1, countWidth bits)) >> 1).resized
      shiftAmount := (posStart.resize(countWidth + 1) |<< 1)

      divisorReg := divisorMag
      remainderReg := dividendMag.resize(remWidth).asSInt
      scaledDivisorReg := (divisorMag.resize(remWidth) |<< shiftAmount).resize(remWidth)
      qpReg := 0
      qnReg := 0
      posReg := posStart
      firstDigitReg := True
      quotientNegReg := quotientNeg
      remainderNegReg := remainderNeg
      busyReg := True
    }
  } elsewhen (finalizeReg) {
    val quotientMag = SInt(qAccWidth + 1 bits)
    val remainderCorrected = SInt(qdsNormWidth bits)

    quotientMag := rawQuotientReg
    remainderCorrected := finalRemainderReg

    when(finalRemainderReg.msb) {
      quotientMag := rawQuotientReg - 1
      remainderCorrected := finalRemainderReg + divisorReg.resize(qdsNormWidth).asSInt
    }

    quotientReg := DividerSupport.applySign(quotientMag.asUInt.resize(dataWidth), quotientNegReg)
    remainderRegOut := DividerSupport.applySign(remainderCorrected.asUInt.resize(dataWidth), remainderNegReg)
    finalizeReg := False
    doneReg := True
  } elsewhen (busyReg) {
    val scaledDivisor = scaledDivisorReg.asSInt
    val scaledDivisorWide = scaledDivisor.resize(qdsNormWidth)
    val remainderWide = remainderReg.resize(qdsNormWidth)

    val qdsShiftToTop = BitMath.leadingZeros(scaledDivisorWide.asBits).resize(qdsShiftWidth)
    val qdsWindowAlign = U(qdsNormWidth - SrtQds.DivBits, qdsShiftWidth bits)
    val qdsDivTop = UInt(SrtQds.DivBits bits)
    val qdsRemTop = SInt(SrtQds.RemBits bits)

    when(qdsShiftToTop <= qdsWindowAlign) {
      val bucketShift = (qdsWindowAlign - qdsShiftToTop).resized
      qdsDivTop := (scaledDivisorWide.asUInt |>> bucketShift).resize(SrtQds.DivBits)
      qdsRemTop := (remainderWide |>> bucketShift).resize(SrtQds.RemBits)
    } otherwise {
      val bucketShift = (qdsShiftToTop - qdsWindowAlign).resized
      qdsDivTop := (scaledDivisorWide.asUInt |<< bucketShift).resize(SrtQds.DivBits)
      qdsRemTop := (remainderWide |<< bucketShift).resize(SrtQds.RemBits)
    }

    val digit = SrtQds.selectDigit(qdsRemTop, qdsDivTop, firstDigitReg)

    val term = SInt(qdsNormWidth bits)
    term := 0
    when(digit === 2) {
      term := scaledDivisorWide |<< 1
    } elsewhen (digit === 1) {
      term := scaledDivisorWide
    } elsewhen (digit === -1) {
      term := -scaledDivisorWide
    } elsewhen (digit === -2) {
      term := -(scaledDivisorWide |<< 1)
    }

    val remainderNext = remainderWide - term

    val qpAppend = UInt(2 bits)
    val qnAppend = UInt(2 bits)
    qpAppend := 0
    qnAppend := 0
    when(digit === 2) {
      qpAppend := 2
    } elsewhen (digit === 1) {
      qpAppend := 1
    } elsewhen (digit === -1) {
      qnAppend := 1
    } elsewhen (digit === -2) {
      qnAppend := 2
    }

    val qpNext = ((qpReg |<< 2) + qpAppend.resize(qAccWidth)).resize(qAccWidth)
    val qnNext = ((qnReg |<< 2) + qnAppend.resize(qAccWidth)).resize(qAccWidth)

    when(posReg === 0) {
      val rawQuotient = qpNext.resize(qAccWidth + 1).asSInt - qnNext.resize(qAccWidth + 1).asSInt
      rawQuotientReg := rawQuotient
      finalRemainderReg := remainderNext
      busyReg := False
      finalizeReg := True
    } otherwise {
      remainderReg := remainderNext.resize(remWidth)
      scaledDivisorReg := (scaledDivisorReg |>> 2).resize(remWidth)
      qpReg := qpNext
      qnReg := qnNext
      posReg := posReg - 1
      firstDigitReg := False
    }
  }
}
