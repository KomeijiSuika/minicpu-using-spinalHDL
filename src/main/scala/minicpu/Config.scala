package minicpu

import spinal.core._

case class CpuConfig(
  xlen: Int = 32,
  resetVector: BigInt = 0x00000000L
)
