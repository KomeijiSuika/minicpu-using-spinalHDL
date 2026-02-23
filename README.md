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
├── local-rv32i/       # 本仓库内置的汇编器与测试程序，引用自https://github.com/pietroglyph/pipelined-rv32i.git
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

```fish
sbt run
# 等价：sbt "runMain minicpu.MyCpuMain"
```

生成文件位于 `rtl/CpuTop.v`。

### 2) 运行所有测试

```fish
sbt test
```

### 3) 单独运行程序级测试（.memh）

默认会运行 `local-rv32i/asm/itypes.memh`：

```fish
sbt "testOnly minicpu.PipelinedRv32iProgramTest"
```

指定程序与周期数：

```fish
sbt -Drv32i.memh=local-rv32i/asm/itypes.memh -Drv32i.maxCycles=300 "testOnly minicpu.PipelinedRv32iProgramTest"
```

也支持直接传 `.s`（测试会自动调用 `local-rv32i/assembler.py` 先生成 `.memh`）：

```fish
env RV32I_PROGRAM=local-rv32i/asm/btypes.s RV32I_MAX_CYCLES=300 sbt "testOnly minicpu.PipelinedRv32iProgramTest"
```

程序级测试现在支持“expected 基线 + 自动比对”：

- 默认 expected 目录：`local-rv32i/expected`
- 默认行为：若 expected 存在则自动逐行比对（`regfile` + `datamem`）
- 若 expected 缺失：测试会提示先生成 expected

首次生成 expected（基线）：

```fish
env RV32I_PROGRAM=local-rv32i/asm/peripherals.s RV32I_MAX_CYCLES=300 RV32I_GEN_EXPECTED=true sbt "testOnly minicpu.PipelinedRv32iProgramTest"
```

后续验证（自动比对，不一致会直接失败）：

```fish
env RV32I_PROGRAM=local-rv32i/asm/peripherals.s RV32I_MAX_CYCLES=300 sbt "testOnly minicpu.PipelinedRv32iProgramTest"
```

可自定义 expected 目录：

```fish
env RV32I_PROGRAM=local-rv32i/asm/peripherals.s RV32I_EXPECTED_DIR=local-rv32i/expected_custom sbt "testOnly minicpu.PipelinedRv32iProgramTest"
```

输出结果会写入 `sim_out/`：

- `regfile_<program>.txt`
- `datamem_<program>.txt`

### 4) 从汇编生成 `.memh`

```fish
python3 -m pip install bitstring
python3 local-rv32i/assembler.py local-rv32i/asm/itypes.s -o local-rv32i/asm/itypes.memh
```

### 5) 测试 local-rv32i 的全部类型程序

`local-rv32i/asm/` 里常见的类型包括：

- `itypes.s`
- `rtypes/irtypes.s`（当前目录中是 `irtypes.s`）
- `btypes.s`
- `lstypes.s`
- `functions.s`
- `delay.s`
- `peripherals.s`
- `peripherals_leds.s`

先安装汇编器依赖（只需一次）：

```fish
python3 -m pip install bitstring
```

#### 方式 A：直接遍历所有 `.s`（推荐）

```fish
for s in local-rv32i/asm/*.s
    echo "===== Testing $s ====="
    env RV32I_PROGRAM="$s" RV32I_MAX_CYCLES=400 sbt "testOnly minicpu.PipelinedRv32iProgramTest"
end
```

说明：每个 `.s` 会先自动汇编到 `sim_out/generated_memh/*.memh`，再运行仿真。

#### 方式 B：先手动批量生成 `.memh`，再逐个测试

```fish
for s in local-rv32i/asm/*.s
    set m (string replace -r '\\.s$' '.memh' -- $s)
    python3 local-rv32i/assembler.py $s -o $m
end

for m in local-rv32i/asm/*.memh
    echo "===== Testing $m ====="
    sbt -Drv32i.memh=$m -Drv32i.maxCycles=400 "testOnly minicpu.PipelinedRv32iProgramTest"
end
```

每次运行后结果导出到 `sim_out/`：

- `regfile_<program>.txt`
- `datamem_<program>.txt`

同时会与 `local-rv32i/expected/` 下同名文件做自动比对（若存在）。

## 测试说明

- `CpuSmokeTest`：最小冒烟仿真，验证顶层可编译与时钟复位流程
- `CpuInstructionTest`：指令级示例测试（ADDI/ADD/SUB/LW/SW/BEQ/JAL/JALR 等）
- `PipelinedRv32iProgramTest`：加载 `.memh/.s` 程序，运行后导出寄存器与数据存储器快照，并可与 expected 自动比对

## 备注

- 生成 Verilog 时若出现 `Mem.readAsync can only be write first`，表示异步读内存在 Verilog 后端按 write-first 语义建模，这是工具行为提示，不是编译错误。
- `Pruned wire detected` 表示未被最终逻辑使用的信号被优化移除，通常不影响功能。

## 参考

- 项目计划文档：[docs/rv32i_5stage_plan.md](docs/rv32i_5stage_plan.md)
- SpinalHDL 文档：https://spinalhdl.github.io/SpinalDoc-RTD/

