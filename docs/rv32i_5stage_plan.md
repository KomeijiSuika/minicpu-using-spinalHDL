# SpinalHDL 练习项目：RV32I 五级流水 CPU 开发计划

> 目标：用 SpinalHDL 从零到一实现一个支持 RV32I 基础整数指令集的五级流水（IF/ID/EX/MEM/WB）CPU，用于学习语言与工程组织。
> 
> 验证目标：可以“输入对应机器码”，并在仿真波形里观察到指令存储器中的具体指令，以及 CPU 当前取到/执行到的指令。

---

## 1. 总体目标与边界

### 1.1 目标

- 指令集：RV32I（基础整数指令集）
- 微结构：五级流水（IF / ID / EX / MEM / WB）
- 冒险处理：
  - 数据冒险：前递（forwarding）+ load-use stall
  - 控制冒险：分支/跳转 flush
- 存储系统（起步阶段）：
  - 指令存储器：单周期读（仿真模型）
  - 数据存储器：单周期读写（仿真模型）

### 1.2 建议边界（先跑通再增强）

为降低难度，建议分两步走：

1) **先做“固定 1-cycle 存储器延迟”的版本**：把流水线与冒险跑通。
2) **再引入可变等待（ready/valid）**：让 i/d memory 出现 stall，从而练习“全流水暂停”控制。

### 1.3 验证方式（必须满足）

- 能把机器码加载到 `InstrMem` 的内部 `Mem` 数组中
- 波形中能直接展开看到 `InstrMem` 的 `mem` 内容（每个 word）
- 波形中能看到 `ifPc` / `ifInst`（或类似 debug 信号），确认 CPU 取到的指令正确

---

## 2. 建议工程目录结构（最小改动 + 可扩展）

你当前工程（`SpinalHDL_minicpu`）里主要 Scala 文件很少：

- `src/main/scala/minicpu/Config.scala`
- `src/main/scala/minicpu/MyCpuMain.scala`
- `src/main/scala/minicpu/core/CpuTop.scala`
- `src/main/scala/minicpu/components/Alu.scala`
- `src/test/scala/minicpu/CpuTest.scala`

建议新增以下包（保持可读性与职责单一）：

```text
src/main/scala/minicpu/
  Config.scala
  MyCpuMain.scala
  core/
    CpuTop.scala                # 顶层：实例化流水线 + 连接 i/d mem
  isa/
    Rv32iEncoding.scala         # 指令字段提取、常量定义（opcode/funct3/funct7）
    ImmGen.scala                # 立即数生成 (I/S/B/U/J)
    Decode.scala                # 译码：inst -> 控制信号(Control)
  bus/
    IBusSimple.scala            # 简易指令接口 bundle（起步可无 ready/valid）
    DBusSimple.scala            # 简易数据接口 bundle（字节写掩码等）
  pipeline/
    PipelineBundles.scala       # IF/ID, ID/EX, EX/MEM, MEM/WB bundles
    HazardUnit.scala            # stall/flush 生成（load-use、分支）
    ForwardUnit.scala           # EX 前递选择
  components/
    Alu.scala                   # ALU（已有）
    RegFile.scala               # 寄存器堆 x0=0
    BranchUnit.scala            # 分支比较/判定（可选）
  mem/
    InstrMem.scala              # 仿真指令存储器：Mem + 初始化 + simPublic
    DataMem.scala               # 仿真数据存储器：读写 + wmask

src/test/scala/minicpu/
  CpuTest.scala                 # 加载机器码、跑周期、观察/断言

docs/
  rv32i_5stage_plan.md           # 本文档（路线图）
```

### 2.1 关于总线/接口的建议

你现在 `CpuTop` 用 `Stream(UInt(32 bits))` 当 iBus/dBus，这更像数据流而不是“按地址访问 memory”。

建议改为更贴合 CPU 的 bundle（起步版本可以不要 ready/valid，或统一做成 1-cycle 访问）：

- `IBusSimple`: `addr -> inst`（只读）
- `DBusSimple`: `addr, we, wdata, wmask -> rdata`（读写）

后续想练习更真实的握手，可以升级：

- 指令侧：`cmd.valid/ready` + `rsp.valid/ready`
- 数据侧：`cmd.valid/ready` + `rsp.valid/ready`

---

## 3. 五级流水：阶段职责与关键信号

### 3.1 IF（取指）

- 输入：PC
- 输出：`ifPc`, `ifInst`
- 逻辑：
  - `pcNext = pc + 4`（默认）
  - 遇到分支/跳转：`pcNext = target`
  - 遇到 stall：PC 保持

### 3.2 ID（译码）

- 从 `ifInst` 提取：opcode/rd/rs1/rs2/funct3/funct7
- `ImmGen` 生成立即数
- 读寄存器堆：`rs1Data`, `rs2Data`
- 译码得到控制信号（例）：
  - `aluOp`, `aluSrcA`, `aluSrcB`
  - `branchType`, `jumpType`
  - `memOp`（load/store/size/sign）
  - `wbSel`（ALU/Load/PC+4/imm…）
  - `regWrite`、`memWrite`

### 3.3 EX（执行）

- ALU 运算、地址计算（load/store address）
- 分支比较/跳转目标计算
- 产生：
  - `takeBranch`, `jump` -> 控制 flush
  - `aluResult`（或 `addr`）

### 3.4 MEM（访存）

- 仅对 load/store 指令发起数据存储器访问
- 处理字节写掩码（SB/SH/SW）
- load 的对齐/符号扩展（LB/LH/LW/LBU/LHU）

### 3.5 WB（写回）

- 根据 `wbSel` 选择写回数据
- 写寄存器堆：`rd`，`regWrite`

---

## 4. 分阶段实现路线（强烈建议按顺序做）

> 原则：每个阶段结束都能仿真跑起来，并能在波形里看到关键 debug 信号。

### 阶段 0：接口与骨架（把 CpuTop 变成“流水线骨架”）

- 重构 `CpuTop`：
  - 用新的 `IBusSimple/DBusSimple`（或先做内部 mem，IO 可后补）
  - 加上 debug 信号：`ifPc`, `ifInst`, `idInst`, `exInst`（至少 `ifPc/ifInst`）
- 目标：PC 能递增，IF 能读到指令（哪怕全部当 NOP）

### 阶段 1：InstrMem + “可视化取指”闭环（最关键的学习闭环）

- 实现 `InstrMem`：内部 `Mem(UInt(32 bits))`
- 在仿真中：
  - 允许从测试写入 mem（通过 `simPublic()` 导出）
  - 波形中能展开 `mem`，看到每个 word 的机器码
- 目标：
  - 你把机器码写进去
  - 波形能看到 `ifInst` 正确读出

### 阶段 2：ID 基础设施（译码 + 立即数 + RegFile）

- 实现 `RegFile`（2读1写，x0=0）
- 实现 `ImmGen`（I/S/B/U/J）
- 实现 `Decode`（从 inst 生成控制信号）
- 先支持一小撮指令快速闭环：
  - `LUI`, `AUIPC`, `ADDI`, `ADD`, `SUB`, `JAL`
- 目标：能跑简单程序并看到寄存器改变

### 阶段 3：EX 分支/跳转 + flush

- 加入 `BEQ/BNE/BLT/BGE/BLTU/BGEU/JALR`
- EX 产生 `redirect`（pc target）与 `flush` 信号
- 实现最简单控制冒险处理：
  - 分支/跳转成立时：flush IF/ID、ID/EX
- 目标：控制流正确，PC 不乱跳

### 阶段 4：MEM 访存（load/store）

- 实现 `DataMem`（仿真用）
- 支持：
  - Loads：`LB/LBU/LH/LHU/LW`
  - Stores：`SB/SH/SW`
- 建议先只支持对齐访问（不对齐先 assert 或当作未定义），后续再补
- 目标：能运行“写内存/读内存”的小程序并验证结果

### 阶段 5：数据冒险处理（Forward + load-use stall）

- `ForwardUnit`：在 EX 阶段为 rs1/rs2 选择：
  - regfile 原值 / 来自 EX/MEM / 来自 MEM/WB
- `HazardUnit`：load-use 冒险：
  - 当 EX 是 load 且 ID 需要 EX.rd -> stall 1 个周期
  - 做法：stall PC 与 IF/ID，向 ID/EX 注入 bubble
- 目标：大多数 RV32I 小程序无需手动插 NOP

### 阶段 6：补齐 RV32I 角落 + 结束机制

- 补齐：
  - 比较：`SLT/SLTU` + 立即数版
  - 移位：`SLL/SRL/SRA` + 立即数版
- `FENCE`：可先当 NOP
- `ECALL/EBREAK`：建议在仿真里触发一个 `halt/trap`，让测试可自然结束

---

## 5. 验证与调试：如何“输入机器码并观察内存指令”

### 方式 A（推荐起步）：测试里直接写入指令存储器

- 在 `InstrMem` 中对 `mem` 做 `simPublic()`（让仿真可写、可在波形展开）
- 在 `CpuTest` 中：
  - 用一个 `Seq[BigInt]` 表示机器码（每个元素是一条 32-bit 指令）
  - 循环写入 `dut.xxx.instrMem.mem.setBigInt(address, value)`（具体 API 依仿真访问方式调整）
  - 运行若干周期，观察 `ifPc/ifInst` 以及 `InstrMem.mem` 内容

优点：最快闭环；不依赖外部工具。

### 方式 B：从 hex 文件初始化（更像真实工作流）

- 定义 hex 文件格式：每行一个 32-bit word（十六进制）
- `InstrMem` 支持从文件初始化（可用 Spinal 的 mem init 机制或仿真加载）
- 测试只要传入文件路径即可

优点：可结合 `pipelined-rv32i/assembler.py` 把 `.s` 变成机器码，再喂入仿真。

### 波形里建议观察的信号清单（最低限度）

- IF：`ifPc`, `ifInst`
- 流水线寄存器 valid/flush/stall 标记
- ID：`rs1/rs2/rd`, `imm`, `control`
- EX：`aluResult`, `takeBranch`, `redirectPc`
- MEM：`dAddr`, `dWe`, `dWmask`, `dWdata`, `dRdata`
- WB：`wbData`, `wbRd`, `wbWriteEnable`
- 指令存储器：`instrMem.mem`（展开查看每个 word）

---

## 6. 每阶段“完成定义”（DoD）

为了避免做到一半卡住，建议每阶段都有“能证明完成”的标准：

- 阶段 1 DoD：
  - 测试能写入指令 mem
  - 波形能看到 mem 内容
  - `ifInst` 与 mem 对应地址内容一致
- 阶段 3 DoD：
  - BEQ/JAL/JALR 能正确改变 PC
  - flush 生效，不会执行到错误路径的指令
- 阶段 5 DoD：
  - 不插 NOP 也能跑常见数据相关序列
  - load-use 会自动 stall 1 个周期

---

## 7. 推荐起步的“最小程序”集合（用于逐步点亮功能）

1) 只含算术：
- `ADDI x1, x0, 1`
- `ADDI x2, x0, 2`
- `ADD  x3, x1, x2`

2) 分支：
- 初始化 x1/x2
- `BEQ` 跳过一条指令

3) 访存：
- `SW` 写
- `LW` 读回

4) load-use 冒险：
- `LW x1, 0(x0)`
- `ADD x2, x1, x1`（期望自动 stall/forward 后正确）

---

## 8. 下一步建议（从现在的工程状态出发）

你当前 `CpuTop` 还是一个 ALU demo。最建议下一步直接做：

1) 新增 `mem/InstrMem.scala`（带 `simPublic`）
2) 在 `CpuTop` 里实现最小 IF：PC + 读 InstrMem
3) 在 `CpuTest` 里直接写入几条机器码，并打开波形看 `InstrMem.mem` 和 `ifInst`

做到这里，你就已经达成了“输入机器码、观察内存指令”的核心验证闭环。
