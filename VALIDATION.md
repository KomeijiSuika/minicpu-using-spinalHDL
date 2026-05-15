# Validation Checklist

本仓库当前的主验证路径已经从早期 `local-rv32i expected` 对比，扩展为两条并存的验证链：

## 1. 规范套件验证

运行：

```bash
sbt "runMain minicpu.validation.ValidationMain TestRV32I"
sbt "runMain minicpu.validation.ValidationMain TestRV32M"
```

固定产物：

- `build/TestRV32I.txt`
- `build/TestRV32M.txt`

通过信号：

- 控制台输出 `PASS`
- 对应 `build/*.txt` 中所有 case 为 `PASS`

覆盖范围：

- `TestRV32I`
  - 基础 ALU
  - load/store
  - branch/jump
  - LUI/AUIPC
- `TestRV32M`
  - `mul/mulh/mulhsu/mulhu`
  - `div/divu/rem/remu`
  - MDU 相关 RAW/WAW / dependent chain

## 2. 既有回归验证

指令级回归：

```bash
sbt "testOnly minicpu.CpuInstructionTest -- -z \"RV32M\""
```

程序级回归：

```bash
sbt "testOnly minicpu.PipelinedRv32iProgramTest"
```

这两条回归仍然保留，用于确保新的 MDU/hazard 重构没有打坏原本主线。

## 3. Legacy 资源说明

`local-rv32i/` 仍然保留，作为历史验证资源和程序样例库，但它已经不再是唯一主验证入口。

- `local-rv32i/tools/assemble_memh.py`
- `local-rv32i/tools/generate_expected.py`
- `local-rv32i/expected/*.txt`

这些资源现在更适合：

- 历史对照
- 手工程序构造
- 额外补充程序样例

而不是作为当前验证体系的唯一判据。
