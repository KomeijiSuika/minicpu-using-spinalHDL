package minicpu

import spinal.core._
import spinal.core.sim._
import minicpu.core.CpuTop
import org.scalatest.funsuite.AnyFunSuite

class CpuTest extends AnyFunSuite {
  test("CpuTop Boot Test") {
    SimConfig.withWave.compile(new CpuTop(CpuConfig())).doSim { dut =>
      dut.clockDomain.forkStimulus(10)
      
      // 复位
      dut.clockDomain.waitSampling(10)
      
      // 运行几个周期
      for(i <- 0 until 20) {
        dut.clockDomain.waitSampling()
      }
    }
  }
}
