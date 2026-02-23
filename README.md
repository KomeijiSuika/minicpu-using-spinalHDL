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
- Python 3（用于 `local-rv32i/tools/*.py` 工具脚本）
- Verilator（`generate_expected.py` 会调用 `pipelined-rv32i` 的 SystemVerilog 仿真）

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

用环境变量传入 `.memh` 与周期：

```fish
env RV32I_MEMH=local-rv32i/asm/btypes.memh RV32I_MAX_CYCLES=300 sbt "testOnly minicpu.PipelinedRv32iProgramTest"
```

输出结果会写入 `sim_out/`：

- `regfile_<program>.txt`
- `datamem_<program>.txt`

### 4) 从汇编生成 `.memh`

```fish
python3 -m pip install bitstring
python3 local-rv32i/tools/assemble_memh.py --all
```

单文件模式：

```fish
python3 local-rv32i/tools/assemble_memh.py --asm local-rv32i/asm/itypes.s
```

### 5) 生成 expected 基线（专用脚本）

由 `pipelined-rv32i` 项目里的 SystemVerilog CPU 仿真直接从 `.memh` 生成 expected（不依赖 Spinal 仿真输出）：

```fish
python3 local-rv32i/tools/generate_expected.py --all --cycles 300
```

单文件模式：

```fish
python3 local-rv32i/tools/generate_expected.py --memh local-rv32i/asm/btypes.memh --cycles 300
```

默认 expected 目录是 `local-rv32i/expected`，可改为自定义目录：

```fish
python3 local-rv32i/tools/generate_expected.py --all --expected-dir local-rv32i/expected_custom
```

### 6) 测试 local-rv32i 的全部类型程序

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

#### 方式 A：先批量组装 + 生成 expected，再回归比对（推荐）

```fish
python3 local-rv32i/tools/assemble_memh.py --all
python3 local-rv32i/tools/generate_expected.py --all --cycles 400

for m in local-rv32i/asm/*.memh
    echo "===== Compare $m ====="
    env RV32I_MEMH="$m" RV32I_MAX_CYCLES=400 sbt "testOnly minicpu.PipelinedRv32iProgramTest"
end
```

#### 方式 B：已存在 expected 时直接回归比对

```fish
for m in local-rv32i/asm/*.memh
    echo "===== Compare $m ====="
    env RV32I_MEMH="$m" RV32I_MAX_CYCLES=400 sbt "testOnly minicpu.PipelinedRv32iProgramTest"
end
```

每次运行后结果导出到 `sim_out/`：

- `regfile_<program>.txt`
- `datamem_<program>.txt`

同时会与 `local-rv32i/expected/` 下同名文件做自动比对（若存在）。

可选：通过 `RV32I_EXPECTED_DIR` 或 `-Drv32i.expectedDir=...` 指定 expected 目录。

## 测试说明

- `CpuSmokeTest`：最小冒烟仿真，验证顶层可编译与时钟复位流程
- `CpuInstructionTest`：指令级示例测试（ADDI/ADD/SUB/LW/SW/BEQ/JAL/JALR 等）
- `PipelinedRv32iProgramTest`：加载 `.memh` 程序，运行后导出寄存器与数据存储器快照，并与 expected 自动比对

## 备注

- 生成 Verilog 时若出现 `Mem.readAsync can only be write first`，表示异步读内存在 Verilog 后端按 write-first 语义建模，这是工具行为提示，不是编译错误。
- `Pruned wire detected` 表示未被最终逻辑使用的信号被优化移除，通常不影响功能。

## 参考

- 项目计划文档：[docs/rv32i_5stage_plan.md](docs/rv32i_5stage_plan.md)
- SpinalHDL 文档：https://spinalhdl.github.io/SpinalDoc-RTD/

