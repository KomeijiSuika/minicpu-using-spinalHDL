# SpinalHDL Mini CPU (RV32I, 5-stage pipeline)

基于 SpinalHDL 的学习型 CPU 项目，当前实现为 RV32I 五级流水线（IF/ID/EX/MEM/WB），包含基础冒险处理与可运行仿真。

## 当前状态

- 流水线：5 级，具备基本取指/译码/执行/访存/写回通路
- 冒险处理：`ForwardUnit` 前递 + `HazardUnit` 的 load-use stall/flush
- 控制流：分支与跳转在 EX 决策，支持 PC 重定向
- 存储器：`InstrMem` / `DataMem`（仿真可见，便于测试）
- Verilog 生成：`minicpu.MyCpuMain` 输出到 `rtl/`

## 目录（与当前代码一致）

```
SpinalHDL_minicpu/
├── build.sbt
├── docs/
│   └── rv32i_5stage_plan.md
├── src/
│   ├── main/scala/minicpu/
│   │   ├── Config.scala
│   │   ├── MyCpuMain.scala
│   │   ├── core/
│   │   │   └── CpuTop.scala
│   │   ├── isa/
│   │   │   ├── Decode.scala
│   │   │   └── Rv32iEncoding.scala
│   │   ├── pipeline/
│   │   │   ├── PipelineBundles.scala
│   │   │   ├── ForwardUnit.scala
│   │   │   └── HazardUnit.scala
│   │   ├── components/
│   │   │   ├── Alu.scala
│   │   │   └── RegFile.scala
│   │   └── mem/
│   │       ├── InstrMem.scala
│   │       └── DataMem.scala
│   └── test/scala/minicpu/
│       ├── CpuSmokeTest.scala
│       ├── CpuInstructionTest.scala
│       └── PipelinedRv32iProgramTest.scala
├── local-rv32i/       # 本仓库内置的汇编器与测试程序
├── rtl/               # 生成的 Verilog
└── sim_out/           # 程序测试导出的寄存器/内存快照
```

## 环境要求

- JDK 8+
- SBT
- Scala 2.12（由 `build.sbt` 指定为 2.12.18）
- Python 3（仅在使用 `local-rv32i/assembler.py` 时需要）

## 常用命令

### 1) 生成 Verilog

```bash
sbt run
# 等价：sbt "runMain minicpu.MyCpuMain"
```

生成文件位于 `rtl/CpuTop.v`。

### 2) 运行所有测试

```bash
sbt test
```

### 3) 单独运行程序级测试（.memh）

默认会运行 `local-rv32i/asm/itypes.memh`：

```bash
sbt "testOnly minicpu.PipelinedRv32iProgramTest"
```

指定程序与周期数：

```bash
sbt -Drv32i.memh=local-rv32i/asm/itypes.memh -Drv32i.maxCycles=300 "testOnly minicpu.PipelinedRv32iProgramTest"
```

输出结果会写入 `sim_out/`：

- `regfile_<program>.txt`
- `datamem_<program>.txt`

### 4) 从汇编生成 `.memh`

```bash
python3 -m pip install bitstring
python3 local-rv32i/assembler.py local-rv32i/asm/itypes.s -o local-rv32i/asm/itypes.memh
```

## 测试说明

- `CpuSmokeTest`：最小冒烟仿真，验证顶层可编译与时钟复位流程
- `CpuInstructionTest`：指令级示例测试（ADDI/ADD/SUB/LW/SW/BEQ/JAL/JALR 等）
- `PipelinedRv32iProgramTest`：加载 `.memh` 程序，运行后导出寄存器与数据存储器快照

## 备注

- 生成 Verilog 时若出现 `Mem.readAsync can only be write first`，表示异步读内存在 Verilog 后端按 write-first 语义建模，这是工具行为提示，不是编译错误。
- `Pruned wire detected` 表示未被最终逻辑使用的信号被优化移除，通常不影响功能。

## 参考

- 项目计划文档：[docs/rv32i_5stage_plan.md](docs/rv32i_5stage_plan.md)
- SpinalHDL 文档：https://spinalhdl.github.io/SpinalDoc-RTD/

