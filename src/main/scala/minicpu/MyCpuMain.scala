package minicpu

import spinal.core._
import spinal.core.sim._
import minicpu.core.CpuTop

object MyCpuMain {
  def main(args: Array[String]): Unit = {
    val config = CpuConfig(xlen = 32)
    
    // 生成 Verilog
    SpinalConfig(
      mode = Verilog,
      targetDirectory = "rtl"
    ).generate(new CpuTop(config)).printPruned()
    
    println("Verilog generated in rtl/ directory.")
  }
}
