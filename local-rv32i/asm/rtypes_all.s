# RV32I R-type full coverage
# Covered: add, sub, sll, slt, sltu, xor, srl, sra, or, and
# Helper I-type instructions are used only to initialize operands.

addi x1, x0, 15
addi x2, x0, 3
addi x3, x0, -8
addi x4, x0, 1
slli x4, x4, 4
addi x5, x0, 31

add  x10, x1, x2      # 15 + 3 = 18
sub  x11, x1, x2      # 15 - 3 = 12
sll  x12, x1, x2      # 15 << 3
slt  x13, x3, x1      # -8 < 15 (signed)
sltu x14, x3, x1      # unsigned compare
xor  x15, x1, x4
srl  x16, x4, x2
sra  x17, x3, x2
or   x18, x1, x4
and  x19, x5, x1

add  x20, x10, x11
sub  x21, x20, x12
sll  x22, x19, x2
slt  x23, x21, x20
sltu x24, x21, x20
xor  x25, x22, x15
srl  x26, x25, x2
sra  x27, x21, x2
or   x28, x27, x26
and  x29, x28, x25

add  x30, x29, x24
sub  x31, x30, x23

# Stable tail (no branches/jumps)
addi x6, x6, 0
addi x7, x7, 0
addi x8, x8, 0
addi x9, x9, 0
