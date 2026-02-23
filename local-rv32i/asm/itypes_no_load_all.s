# RV32I I-type (without Load) coverage
# Covered: addi, slli, slti, sltiu, xori, srli, srai, ori, andi, jalr

addi x1, x0, 17
addi x2, x0, -9
addi x3, x0, 5

slli x4, x1, 3
slti x5, x2, -1
sltiu x6, x2, 100
xori x7, x4, 0x155
srli x8, x7, 2
srai x9, x2, 1
ori  x10, x8, 0x20
andi x11, x10, 0x1ff

addi x12, x11, -128
slli x13, x12, 1
srli x14, x13, 3
srai x15, x12, 4
xori x16, x15, 0x3a
ori  x17, x16, 0x44
andi x18, x17, 0x7f
slti x19, x12, 0
sltiu x20, x13, 1024

# continue I-type chain
addi x25, x24, -7
slli x26, x25, 2
srli x27, x26, 1
srai x28, x25, 3
xori x29, x28, 0x1a
ori  x30, x29, 0x100
andi x31, x30, 0x3ff

addi x1, x31, 1
slti x2, x1, 600
sltiu x3, x1, 700

# jalr coverage (placed at tail to avoid disrupting earlier checks)
# Jump to address 0 (program head)
jalr x22, x0, 0
