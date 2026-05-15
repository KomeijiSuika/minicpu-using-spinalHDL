package minicpu.mdu

import minicpu.CpuConfig
import minicpu.pipeline.{ResultSrc, idExReg, mduWbReg}
import spinal.core._
import spinal.lib._

object MulState extends SpinalEnum {
  val idle = newElement("idle")
  val busy = newElement("busy")
  val hold = newElement("hold")
}

object DivState extends SpinalEnum {
  val idle = newElement("idle")
  val busy = newElement("busy")
  val hold = newElement("hold")
}

case class MulUnit(config: CpuConfig) extends Component {
  val io = new Bundle {
    val wb = out(mduWbReg(config))
    val regDE = in(idExReg(config))
    val rs1DataE = in Bits(config.xlen bits)
    val rs2DataE = in Bits(config.xlen bits)
    val rdMul = out UInt(5 bits)
    val resultSrcMul = out(ResultSrc())
    val regWriteMul = out Bool()
    val stallMulW = in Bool()
  }

  val multiplier = Multiplier(config).setName("multiplier_u")
  val state = Reg(MulState()) init(MulState.idle)
  val mulInput = io.regDE.regWriteEnable && io.regDE.resultSrc === ResultSrc.mul
  val launchMul = state === MulState.idle && mulInput

  multiplier.io.start := launchMul
  multiplier.io.srca := io.rs1DataE
  multiplier.io.srcb := io.rs2DataE
  multiplier.io.mulOp := io.regDE.mulOp

  val pendingRd = Reg(UInt(5 bits)) init(0)
  val holdResult = Reg(UInt(config.xlen bits)) init(0)
  val holdRd = Reg(UInt(5 bits)) init(0)

  val readyNow = state === MulState.busy && multiplier.io.done

  switch(state) {
    is(MulState.idle) {
      when(launchMul) {
        pendingRd := io.regDE.rd
        state := MulState.busy
      }
    }
    is(MulState.busy) {
      when(multiplier.io.done) {
        when(io.stallMulW) {
          holdResult := multiplier.io.result.asUInt
          holdRd := pendingRd
          state := MulState.hold
        } otherwise {
          state := MulState.idle
        }
      }
    }
    is(MulState.hold) {
      when(!io.stallMulW) {
        state := MulState.idle
      }
    }
  }

  io.wb.rd := 0
  io.wb.result := 0
  io.wb.regWrite := False
  io.wb.resultSrc := ResultSrc.alu

  when(readyNow && !io.stallMulW) {
    io.wb.rd := pendingRd
    io.wb.result := multiplier.io.result.asUInt
    io.wb.regWrite := True
    io.wb.resultSrc := ResultSrc.mul
  } elsewhen (state === MulState.hold && !io.stallMulW) {
    io.wb.rd := holdRd
    io.wb.result := holdResult
    io.wb.regWrite := True
    io.wb.resultSrc := ResultSrc.mul
  }

  switch(state) {
    is(MulState.idle) {
      io.resultSrcMul := Mux(mulInput, ResultSrc.mul, ResultSrc.pc4)
      io.regWriteMul := False
      io.rdMul := Mux(mulInput, io.regDE.rd, U(0, 5 bits))
    }
    is(MulState.busy) {
      io.resultSrcMul := ResultSrc.mul
      io.regWriteMul := readyNow
      io.rdMul := pendingRd
    }
    is(MulState.hold) {
      io.resultSrcMul := ResultSrc.mul
      io.regWriteMul := True
      io.rdMul := holdRd
    }
  }
}

case class DivUnit(config: CpuConfig) extends Component {
  val io = new Bundle {
    val wb = out(mduWbReg(config))
    val regDE = in(idExReg(config))
    val rs1DataE = in Bits(config.xlen bits)
    val rs2DataE = in Bits(config.xlen bits)
    val rdDiv = out UInt(5 bits)
    val resultSrcDiv = out(ResultSrc())
    val regWriteDiv = out Bool()
    val stallDivW = in Bool()
  }

  val divider = Divider(config).setName("divider_u")
  val state = Reg(DivState()) init(DivState.idle)
  val divInput = io.regDE.regWriteEnable && io.regDE.resultSrc === ResultSrc.div
  val divDone = divider.io.done
  val pendingRd = Reg(UInt(5 bits)) init(0)
  val holdResult = Reg(UInt(config.xlen bits)) init(0)
  val holdRd = Reg(UInt(5 bits)) init(0)

  divider.io.start := state === DivState.idle && divInput
  divider.io.srca := io.rs1DataE
  divider.io.srcb := io.rs2DataE
  divider.io.divOp := io.regDE.divOp

  switch(state) {
    is(DivState.idle) {
      when(divInput) {
        pendingRd := io.regDE.rd
        state := DivState.busy
      }
    }
    is(DivState.busy) {
      when(divDone) {
        when(io.stallDivW) {
          holdResult := divider.io.result.asUInt
          holdRd := pendingRd
          state := DivState.hold
        } otherwise {
          state := DivState.idle
        }
      }
    }
    is(DivState.hold) {
      when(!io.stallDivW) {
        state := DivState.idle
      }
    }
  }

  io.wb.rd := 0
  io.wb.result := 0
  io.wb.regWrite := False
  io.wb.resultSrc := ResultSrc.alu

  when(!io.stallDivW && state === DivState.busy && divDone) {
    io.wb.rd := pendingRd
    io.wb.result := divider.io.result.asUInt
    io.wb.regWrite := True
    io.wb.resultSrc := ResultSrc.div
  } elsewhen (!io.stallDivW && state === DivState.hold) {
    io.wb.rd := holdRd
    io.wb.result := holdResult
    io.wb.regWrite := True
    io.wb.resultSrc := ResultSrc.div
  }

  switch(state) {
    is(DivState.idle) {
      io.resultSrcDiv := Mux(divInput, ResultSrc.div, ResultSrc.pc4)
      io.regWriteDiv := False
      io.rdDiv := Mux(divInput, io.regDE.rd, U(0, 5 bits))
    }
    is(DivState.busy) {
      io.resultSrcDiv := ResultSrc.div
      io.regWriteDiv := divDone && !io.stallDivW
      io.rdDiv := pendingRd
    }
    is(DivState.hold) {
      io.resultSrcDiv := ResultSrc.div
      io.regWriteDiv := True
      io.rdDiv := holdRd
    }
  }
}
