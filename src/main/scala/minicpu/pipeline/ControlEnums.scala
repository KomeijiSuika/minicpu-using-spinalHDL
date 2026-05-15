package minicpu.pipeline

import spinal.core._

object ResultSrc extends SpinalEnum {
  val alu = newElement("alu")
  val mem = newElement("mem")
  val pc4 = newElement("pc4")
  val mul = newElement("mul")
  val div = newElement("div")
}
