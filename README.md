# SpinalHDL 五级流水 RV32I MiniCPU

本仓库使用 SpinalHDL 实现了一个 **RV32I 五级流水线（IF/ID/EX/MEM/WB）CPU**，用于学习与验证。

## 1) CPU 特性

- 五级流水线 RV32I
- **无分支预测**（分支/跳转在 EX 阶段决策并重定向 PC）
- 有冒险（hazard）和前递（forward）模块
- 支持 RV32I 基础指令集中的常用指令
    - **不支持**：`ecall`、`ebreak`
    - **不支持任何扩展指令**（如 M/A/C 等）

## 2) DataMemory 的 4 个 bank（对齐参考模型用）

为方便用参考项目 **pipelined-rv32i**（SystemVerilog + Verilator）（https://github.com/pietroglyph/pipelined-rv32i.git）交叉验证，本项目的 `DataMem` 将地址空间按高位划分为 4 个 bank（通常通过 `addr[31:28]` 选择）：

- `0x0`：Instruction bank（指令存储）
- `0x1`：MMRS bank（内存映射寄存器/外设）
- `0x2`：VRAM bank（显存/视频相关）
- `0x3`：Data bank（数据内存）

这样做的目的是：在与 pipelined-rv32i 的参考 CPU 做对比时，尽量让两边的“哪些地址属于数据存储、哪些属于外设/指令”等访问语义保持一致，避免因为地址映射不同导致的假失败。

注意：**即便做了对齐，目前仍存在少量 load 指令在两个项目之间的行为不一致**（需要继续定位对齐点/边界条件）。

## 3) 验证方式（汇编驱动 + 参考 expected）

验证流程基于汇编程序：

1. 使用 [local-rv32i/tools/assemble_memh.py](local-rv32i/tools/assemble_memh.py) 将 `.s` 汇编为机器码格式的 `.memh`
2. 使用 [local-rv32i/tools/generate_expected.py](local-rv32i/tools/generate_expected.py) 调用 pipelined-rv32i 参考 CPU 生成期望结果（expected）：
     - 输出期望的 `regfile_*.txt` 与 `datamem_*.txt`
     - 该参考链路使用的 Verilator 版本为 **5.044**（以你的验证环境为准）
3. 使用本 Spinal 项目运行同一个 `.memh`（本项目测试环境的 Verilator 为 **4.228**），导出本项目的 `sim_out/regfile_*.txt` 与 `sim_out/datamem_*.txt`
4. 将本项目导出的快照与 expected 文件逐行比对

这套流程的目标是：让本项目的实现通过“同一程序、同一检查点（regfile/datamem）”与参考实现进行一致性验证。

## 4) 运行指令（可直接复制）

下面命令默认在仓库根目录执行。

说明：下面示例使用了本机的 conda 绝对路径 `/home/user/apps/miniconda3/bin/conda`；如果你的 `conda` 已在 `PATH` 中，可直接把它替换为 `conda`。

### 4.1 一次性准备（Python 依赖）

`assemble_memh.py` / `generate_expected.py` 依赖 `bitstring`：

```fish
python3 -m pip install bitstring
```

如果你用 conda 环境隔离工具链，建议在对应环境内安装：

```fish
/home/user/apps/miniconda3/bin/conda run -n systemverilog python3 -m pip install bitstring
```

### 4.2 生成 .memh（从汇编 .s）

批量组装全部程序：

```fish
/home/user/apps/miniconda3/bin/conda run -n systemverilog \
    python3 local-rv32i/tools/assemble_memh.py --all
```

单文件组装：

```fish
/home/user/apps/miniconda3/bin/conda run -n systemverilog \
    python3 local-rv32i/tools/assemble_memh.py --asm local-rv32i/asm/itypes.s
```

### 4.3 生成 expected（参考 pipelined-rv32i）

单个程序：

```fish
/home/user/apps/miniconda3/bin/conda run -n systemverilog \
    python3 local-rv32i/tools/generate_expected.py --memh local-rv32i/asm/stypes_all.memh --cycles 300
```

批量生成全部 expected：

```fish
/home/user/apps/miniconda3/bin/conda run -n systemverilog \
    python3 local-rv32i/tools/generate_expected.py --all --cycles 300
```

生成结果位于 `local-rv32i/expected/`。

### 4.4 运行本项目并对比（Spinal 仿真）

单个程序对比（指定 `.memh` 与最大周期）：

```fish
env RV32I_MEMH=local-rv32i/asm/stypes_all.memh RV32I_MAX_CYCLES=300 \
    /home/user/apps/miniconda3/bin/conda run -n my-spinal \
    sbt "testOnly minicpu.PipelinedRv32iProgramTest"
```

批量对比所有 `.memh`：

```fish
for m in local-rv32i/asm/*.memh
        echo "===== Compare $m ====="
        env RV32I_MEMH="$m" RV32I_MAX_CYCLES=300 \
            /home/user/apps/miniconda3/bin/conda run -n my-spinal \
            sbt "testOnly minicpu.PipelinedRv32iProgramTest"
end
```

对比输出：

- 本项目输出在 `sim_out/`：`regfile_<program>.txt`、`datamem_<program>.txt`
- 参考 expected 在 `local-rv32i/expected/`：`regfile_<program>.txt`、`datamem_<program>.txt`

### 4.5 生成 Verilog（可选）

```fish
sbt "runMain minicpu.MyCpuMain"
```

生成的 Verilog 位于 `rtl/`。

每次运行后结果导出到 `sim_out/`：

- `regfile_<program>.txt`
- `datamem_<program>.txt`

同时会与 `local-rv32i/expected/` 下同名文件做自动比对（若存在）。

可选：通过 `RV32I_EXPECTED_DIR` 或 `-Drv32i.expectedDir=...` 指定 expected 目录。

说明：`PipelinedRv32iProgramTest` 本身不会调用 `pipelined-rv32i`；它只读取 `local-rv32i/expected/` 做比对。

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

