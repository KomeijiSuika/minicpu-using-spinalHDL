# RV32I comprehensive program (without B-type / J-type)
# Covers: U-type, I-type ALU, R-type ALU, Load/Store
# No branch / jump instructions are used.

# ---------- U-type bootstrap ----------
lui   x1, 0x12345
auipc x2, 0
addi  x3, x0, 0
addi  x4, x0, 256
add   x5, x1, x2
sub   x6, x5, x1
xor   x7, x6, x4
or    x8, x7, x1
and   x9, x8, x5

# ---------- I-type arithmetic / logic ----------
addi  x10, x0, 17
addi  x11, x0, -9
slli  x12, x10, 3
srli  x13, x12, 1
srai  x14, x11, 2
slti  x15, x11, -1
sltiu x16, x11, 20
xori  x17, x12, 0x15a
ori   x18, x17, 0x201
andi  x19, x18, 0x0ff

# ---------- R-type full set (1) ----------
add   x20, x10, x12
sub   x21, x20, x13
sll   x22, x10, x15
slt   x23, x11, x10
sltu  x24, x11, x10
xor   x25, x20, x21
srl   x26, x25, x15
sra   x27, x11, x15
or    x28, x26, x27
and   x29, x28, x25

# ---------- Prepare memory payload ----------
addi  x30, x0, 0x11
slli  x30, x30, 8
addi  x30, x30, 0x22
slli  x30, x30, 8
addi  x30, x30, 0x33
slli  x30, x30, 8
addi  x30, x30, 0x44

addi  x31, x0, -1
srli  x31, x31, 1
xori  x31, x31, 0x55

# ---------- Store family ----------
sw    x30, 0(x3)
sw    x31, 4(x3)
sh    x30, 8(x3)
sh    x31, 12(x3)
sb    x30, 16(x3)
sb    x31, 20(x3)
sw    x20, 24(x3)
sw    x21, 28(x3)

# ---------- Load family ----------
lw    x5, 0(x3)
lw    x6, 4(x3)
lh    x7, 8(x3)
lhu   x8, 8(x3)
lh    x9, 12(x3)
lhu   x10, 12(x3)
lb    x11, 16(x3)
lbu   x12, 16(x3)
lb    x13, 20(x3)
lbu   x14, 20(x3)

# ---------- Mixed dependency chain ----------
add   x15, x5, x7
sub   x16, x15, x9
xor   x17, x16, x6
or    x18, x17, x8
and   x19, x18, x10
slt   x20, x11, x12
sltu  x21, x13, x14
sll   x22, x19, x20
srl   x23, x22, x21
sra   x24, x16, x20

addi  x25, x23, -128
xori  x26, x24, 0x3a5
ori   x27, x26, 0x040
andi  x28, x27, 0x3ff
slti  x29, x25, 0
sltiu x30, x26, 1024

# ---------- More memory traffic ----------
sw    x25, 32(x3)
sw    x26, 36(x3)
sw    x27, 40(x3)
sw    x28, 44(x3)
sh    x29, 48(x3)
sh    x30, 52(x3)
sb    x20, 56(x3)
sb    x21, 60(x3)

lw    x1, 32(x3)
lw    x2, 36(x3)
lw    x4, 40(x3)
lw    x5, 44(x3)
lh    x6, 48(x3)
lhu   x7, 48(x3)
lb    x8, 56(x3)
lbu   x9, 56(x3)

# ---------- U-type reuse + ALU stress ----------
auipc x10, 0
lui   x11, 0x00abc
add   x12, x10, x11
sub   x13, x12, x2
xor   x14, x13, x1
or    x15, x14, x4
and   x16, x15, x5
sll   x17, x16, x20
srl   x18, x17, x21
sra   x19, x13, x20

# ---------- Final write-back pressure ----------
addi  x22, x0, 7
slli  x22, x22, 5
add   x23, x22, x19
sub   x24, x23, x18
xori  x25, x24, 0x2aa
ori   x26, x25, 0x155
andi  x27, x26, 0x7ff
slti  x28, x27, 500
sltiu x29, x27, 1500

sw    x23, 64(x3)
sw    x24, 68(x3)
sw    x25, 72(x3)
sw    x26, 76(x3)
sw    x27, 80(x3)
sw    x28, 84(x3)
sw    x29, 88(x3)

lw    x30, 64(x3)
lw    x31, 68(x3)
add   x1, x30, x31
sub   x2, x1, x29
xor   x3, x2, x28
or    x4, x3, x27
and   x5, x4, x26
