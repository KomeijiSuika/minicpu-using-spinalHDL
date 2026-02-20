# SpinalHDL 迷你 CPU 项目

> 用 **SpinalHDL** 从零到一实现一个支持 **RV32I 基础整数指令集**的**五级流水线 CPU**，用于学习和练习 SpinalHDL HDL 语言与 CPU 微结构设计。

## 📋 项目简介

本项目是一个**学习驱动**的 RISC-V CPU 实现，旨在通过完整的硬件设计流程掌握：

- **SpinalHDL 语言特性**：从基础 Bundle、Component 到流水线控制、前递/冒险处理的设计模式
- **五级流水线微结构**：IF（取指）→ ID（译码）→ EX（执行）→ MEM（访存）→ WB（写回）
- **数据冒险处理**：前递（forwarding）+ load-use stall
- **控制冒险处理**：分支/跳转的 flush 与 PC 重定向
- **仿真验证方法**：直接在波形里观察指令存储器、寄存器堆、管道信号变化

### 核心特点

| 特性 | 说明 |
|------|------|
| 指令集 | RISC-V RV32I（32 条基础整数指令） |
| 流水线 | 5 级：IF → ID → EX → MEM → WB |
| 存储器 | 仿真用指令/数据存储器（支持单周期访问） |
| 冒险处理 | 前递、load-use stall、分支 flush |
| 验证方式 | 直接写入机器码，波形可视化指令执行 |

---

## 🗂️ 项目结构

```
SpinalHDL_minicpu/
├── README.md                        # 项目说明（本文件）
├── docs/
│   └── rv32i_5stage_plan.md        # 详细实现路线图（必读！）
├── build.sbt                       # Scala/SBT 构建配置
├── src/
│   ├── main/scala/minicpu/
│   │   ├── Config.scala            # CPU 全局配置
│   │   ├── MyCpuMain.scala         # HDL 生成入口
│   │   ├── core/
│   │   │   └── CpuTop.scala        # 顶层：实例化流水线 + 存储器
│   │   ├── isa/                    # RISC-V 指令编码与译码
│   │   │   ├── Rv32iEncoding.scala # 指令字段提取
│   │   │   ├── ImmGen.scala        # 立即数生成
│   │   │   └── Decode.scala        # 指令译码 -> 控制信号
│   │   ├── bus/                    # 总线接口定义
│   │   │   ├── IBusSimple.scala    # 指令总线 bundle
│   │   │   └── DBusSimple.scala    # 数据总线 bundle
│   │   ├── pipeline/               # 流水线核心
│   │   │   ├── PipelineBundles.scala  # IF/ID、ID/EX、EX/MEM、MEM/WB
│   │   │   ├── HazardUnit.scala    # 冒险检测（stall/flush）
│   │   │   └── ForwardUnit.scala   # 数据前递选择
│   │   ├── components/             # 可复用部件
│   │   │   ├── Alu.scala           # 算术逻辑单元
│   │   │   ├── RegFile.scala       # 寄存器堆（32x32）
│   │   │   └── BranchUnit.scala    # 分支条件判定
│   │   └── mem/                    # 仿真存储器
│   │       ├── InstrMem.scala      # 指令存储器（可可视化）
│   │       └── DataMem.scala       # 数据存储器
│   └── test/scala/minicpu/
│       └── CpuTest.scala           # 仿真测试与验证
├── rtl/                            # 生成的 Verilog 输出
```

---

## 🚀 快速开始

### 前置要求

- Java JDK 8+
- Scala 2.12+
- SBT（Scala Build Tool）
- Verilator（可选，用于仿真波形生成）

### 编译与生成 Verilog

```bash
cd /home/shenby/workplace/learning_SpinalHDL/SpinalHDL_minicpu
sbt run  # 或 sbt "runMain minicpu.MyCpuMain"
```

位于 `rtl/` 目录下生成 Verilog。

### 仿真与波形

```bash
# 方式 1：使用 SBT 内置仿真（推荐起步）
sbt test

# 方式 2：生成 Verilog 后用 Verilator 仿真（后期）
verilator --cc rtl/CpuTop.v --trace
# 编译 + 运行生成 .vcd 波形
```

---

## 📝 实现路线图

项目按**阶段**递进实现，每阶段都能单独仿真验证。详见 [docs/rv32i_5stage_plan.md](docs/rv32i_5stage_plan.md)。

### 快速列表

- **阶段 0**：接口与骨架（PC 递增、IF 读指令）
- **阶段 1**：`` InstrMem + 可视化取指闭环 ``（**本月目标**）
- **阶段 2**：ID 基础设施（译码、立即数、寄存器堆）
- **阶段 3**：EX 分支/跳转 + flush
- **阶段 4**：MEM 访存指令（load/store）
- **阶段 5**：数据冒险处理（前递 + stall）
- **阶段 6**：补齐 RV32I 全集 + 结束机制

---

## 🧪 验证策略："输入机器码 → 观察指令存储器"

本项目最核心的验证方式：

1. **编写或汇编** RISC-V 指令序列：
   ```s
   ADDI x1, x0, 1
   ADDI x2, x0, 2
   ADD  x3, x1, x2
   JAL  x0, -12       # 无限循环
   ```

2. **转换为机器码**（十六进制或二进制），在测试中写入 `InstrMem`

3. **仿真运行**，打开波形查看：
   - `dirMem.mem[]` 数组（直接展开查看每个 word）
   - `ifId_inst` / `exMem_inst`（各阶段当前指令）
   - 寄存器堆与数据存储器的变化

4. **验证**：
   - 指令执行顺序是否正确
   - 寄存器值是否符合预期
   - 分支/跳转是否生效

---

## 📚 参考资源

### 本仓库内

- [docs/rv32i_5stage_plan.md](docs/rv32i_5stage_plan.md) — **必读**：详细实现计划、每阶段 DoD、测试用例建议


### 外部资源

- **SpinalHDL 官方**：[https://spinalhdl.github.io/SpinalDoc-RTD/](https://spinalhdl.github.io/SpinalDoc-RTD/)
  - 特别推荐：Stream、Bundle、模拟（Simulation）章节
- **RISC-V 指令集规范**：[https://riscv.org/](https://riscv.org/)
  - 免费下载 ISA Manual（第 I 章即 RV32I）
- **Digital Design & Computer Architecture（RISC-V 版）**：经典教科书，五级流水讲解清楚

### 可视化工具

- **GTKWave** 或 **WaveForm Viewer**：查看 `.vcd` 波形文件
- **draw.io**：绘制流水线图（可参考 VexRiscv 的 diagram.drawio）

---

## 🎯 学习目标检查清单

完成后应能理解和实现：

- [ ] SpinalHDL 的 Component、Bundle、Signal 基本概念
- [ ] 五级流水线的每一级职责
- [ ] 各类冒险（数据、控制）的检测与处理机制
- [ ] 在 Scala/Spinal 中设计并验证一个完整的 CPU
- [ ] 用波形观察 CPU 内部信号，进行调试
- [ ] 从 RISC-V 汇编到机器码的全流程

