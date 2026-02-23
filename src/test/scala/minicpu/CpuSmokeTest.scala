package minicpu

import minicpu.core.CpuTop
import org.scalatest.funsuite.AnyFunSuite
import spinal.core.sim._

class CpuSmokeTest extends AnyFunSuite {
  test("CpuTop smoke sim") {
    SimConfig.compile(new CpuTop(CpuConfig())).doSim { dut =>
      dut.clockDomain.forkStimulus(10)
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()
      dut.clockDomain.waitSampling(20)
    }
  }
}
