name := "spinalhdl_learn"

version := "1.0"

scalaVersion := "2.12.18"

fork := true

libraryDependencies ++= Seq(
  "com.github.spinalhdl" %% "spinalhdl-core" % "1.7.0",
  "com.github.spinalhdl" %% "spinalhdl-lib" % "1.7.0",
  "com.github.spinalhdl" %% "spinalhdl-sim" % "1.7.0",
  "org.scalatest" %% "scalatest" % "3.2.14" % "test"
)

addCompilerPlugin("com.github.spinalhdl" %% "spinalhdl-idsl-plugin" % "1.7.0")
