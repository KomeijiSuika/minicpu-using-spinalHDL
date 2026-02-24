# RV32I complex test (NO U/J/B/L)
# Allowed: I-ALU, R-ALU, S(store)
# Forbidden: lui, auipc, jal, jalr, branch, load

addi  x1,  x0, 123
addi  x2,  x0, -45
addi  x3,  x0, 31
addi  x4,  x0, 7
addi  x5,  x0, 255

slli  x6,  x1, 3
srli  x7,  x6, 2
srai  x8,  x2, 1
xori  x9,  x6, 0x155
ori   x10, x9, 0x22
andi  x11, x10, 0x1ff
slti  x12, x2, -1
sltiu x13, x2, 32

add   x14, x1, x6
sub   x15, x14, x7
sll   x16, x1, x4
slt   x17, x2, x1
sltu  x18, x2, x1
xor   x19, x14, x15
srl   x20, x19, x12
sra   x21, x2, x12
or    x22, x20, x21
and   x23, x22, x19

addi  x24, x23, -77
xori  x25, x24, 0x3aa
ori   x26, x25, 0x041
andi  x27, x26, 0x2ff
slti  x28, x24, 0
sltiu x29, x25, 900
slli  x30, x27, 2
srli  x31, x30, 3

add   x1,  x30, x31
sub   x2,  x1,  x8
xor   x3,  x2,  x27
or    x4,  x3,  x29
and   x5,  x4,  x28
sll   x6,  x1,  x29
srl   x7,  x6,  x28
sra   x8,  x2,  x29
slt   x9,  x2,  x1
sltu  x10, x3,  x4

# store pass 1
sw    x1,  0(x0)
sw    x2,  4(x0)
sw    x3,  8(x0)
sw    x4,  12(x0)
sw    x5,  16(x0)
sh    x6,  20(x0)
sh    x7,  24(x0)
sh    x8,  28(x0)
sb    x9,  32(x0)
sb    x10, 36(x0)

# more dependency pressure
addi  x11, x0, 7
slli  x11, x11, 4
add   x12, x11, x23
sub   x13, x12, x22
xor   x14, x13, x21
or    x15, x14, x20
and   x16, x15, x19
slti  x17, x16, 500
sltiu x18, x15, 1500
xori  x19, x14, 0x1b3
ori   x20, x19, 0x44
andi  x21, x20, 0x7ff

# store pass 2
sw    x12, 40(x0)
sw    x13, 44(x0)
sw    x14, 48(x0)
sw    x15, 52(x0)
sw    x16, 56(x0)
sh    x17, 60(x0)
sh    x18, 64(x0)
sb    x19, 68(x0)
sb    x20, 72(x0)
sw    x21, 76(x0)
