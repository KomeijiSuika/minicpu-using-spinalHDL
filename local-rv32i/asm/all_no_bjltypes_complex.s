# RV32I comprehensive program (NO B-type / J-type / L-type)
# Covered types: U, I-ALU, R-ALU, S(store)
# Covered instructions:
#   U:    lui, auipc
#   I:    addi, slli, slti, sltiu, xori, srli, srai, ori, andi
#   R:    add, sub, sll, slt, sltu, xor, srl, sra, or, and
#   S:    sb, sh, sw

# ---------- U-type bootstrap ----------
lui   x1, 0x12345
auipc x2, 0
addi  x3, x0, 0
addi  x4, x0, 4
addi  x5, x0, 8
addi  x6, x0, 12
addi  x7, x0, 16
addi  x8, x0, 20
addi  x9, x0, 24
addi  x10, x0, 28

# ---------- I-type seed values ----------
addi  x11, x0, 17
addi  x12, x0, -9
addi  x13, x0, 63
addi  x14, x0, 5
slli  x15, x11, 3
srli  x16, x15, 2
srai  x17, x12, 1
xori  x18, x15, 0x155
ori   x19, x18, 0x22
andi  x20, x19, 0x1ff
slti  x21, x12, -1
sltiu x22, x12, 32

# ---------- R-type pass 1 ----------
add   x23, x11, x15
sub   x24, x23, x16
sll   x25, x11, x14
slt   x26, x12, x11
sltu  x27, x12, x11
xor   x28, x23, x24
srl   x29, x28, x21
sra   x30, x12, x21
or    x31, x29, x30
and   x1, x31, x28

# ---------- I/R mixed dependency chain ----------
addi  x2, x1, -77
xori  x3, x2, 0x3aa
ori   x4, x3, 0x041
andi  x5, x4, 0x2ff
slti  x6, x2, 0
sltiu x7, x3, 900
slli  x8, x5, 2
srli  x9, x8, 3
srai  x10, x2, 4
add   x11, x8, x9
sub   x12, x11, x10
xor   x13, x12, x5
or    x14, x13, x7
and   x15, x14, x6
sll   x16, x11, x7
srl   x17, x16, x6
sra   x18, x12, x7
slt   x19, x12, x11
sltu  x20, x13, x14

# ---------- Store pass 1 ----------
sw    x11, 0(x0)
sw    x12, 4(x0)
sw    x13, 8(x0)
sw    x14, 12(x0)
sw    x15, 16(x0)
sh    x16, 20(x0)
sh    x17, 24(x0)
sh    x18, 28(x0)
sb    x19, 32(x0)
sb    x20, 36(x0)

# ---------- U-type reuse ----------
auipc x21, 0
lui   x22, 0x00abc
add   x23, x21, x22
sub   x24, x23, x2
xor   x25, x24, x3
or    x26, x25, x4
and   x27, x26, x5
sll   x28, x27, x6
srl   x29, x28, x7
sra   x30, x24, x6

# ---------- I-type stress ----------
addi  x31, x30, 127
addi  x1, x31, -128
xori  x2, x1, 0x2a5
ori   x3, x2, 0x140
andi  x4, x3, 0x3ff
slti  x5, x1, 1
sltiu x6, x2, 1024
slli  x7, x4, 1
srli  x8, x7, 1
srai  x9, x1, 3

# ---------- R-type pass 2 ----------
add   x10, x7, x8
sub   x11, x10, x9
sll   x12, x10, x5
slt   x13, x11, x10
sltu  x14, x11, x10
xor   x15, x11, x12
srl   x16, x15, x6
sra   x17, x11, x5
or    x18, x16, x17
and   x19, x18, x15

# ---------- Store pass 2 ----------
sw    x23, 40(x0)
sw    x24, 44(x0)
sw    x25, 48(x0)
sw    x26, 52(x0)
sw    x27, 56(x0)
sh    x28, 60(x0)
sh    x29, 64(x0)
sh    x30, 68(x0)
sb    x31, 72(x0)
sb    x1, 76(x0)

# ---------- Final arithmetic pressure ----------
addi  x20, x0, 7
slli  x20, x20, 4
add   x21, x20, x19
sub   x22, x21, x18
xor   x23, x22, x17
or    x24, x23, x16
and   x25, x24, x15
slti  x26, x25, 500
sltiu x27, x24, 1500
xori  x28, x23, 0x1b3
ori   x29, x28, 0x44
andi  x30, x29, 0x7ff

# ---------- Final stores ----------
sw    x21, 80(x0)
sw    x22, 84(x0)
sw    x23, 88(x0)
sw    x24, 92(x0)
sw    x25, 96(x0)
sh    x26, 100(x0)
sh    x27, 104(x0)
sb    x28, 108(x0)
sb    x29, 112(x0)
sw    x30, 116(x0)
