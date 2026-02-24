# RV32I full coverage without load instructions
# Covered:
#   U:    lui, auipc
#   I:    addi, slli, slti, sltiu, xori, srli, srai, ori, andi, jalr
#   R:    add, sub, sll, slt, sltu, xor, srl, sra, or, and
#   S:    sb, sh, sw
#   B:    beq, bne, blt, bge, bltu, bgeu
#   J:    jal
# Forbidden:
#   lb, lh, lw, lbu, lhu

# ---------- U-type ----------
lui   x1, 0x12345
auipc x2, 0

# ---------- I-type seeds ----------
addi  x3,  x0, 17
addi  x4,  x0, -9
slli  x5,  x3, 3
srli  x6,  x5, 2
srai  x7,  x4, 1
xori  x8,  x5, 0x155
ori   x9,  x8, 0x22
andi  x10, x9, 0x1ff
slti  x11, x4, -1
sltiu x12, x4, 32

# ---------- R-type ----------
add   x13, x3, x5
sub   x14, x13, x6
sll   x15, x3, x11
slt   x16, x4, x3
sltu  x17, x4, x3
xor   x18, x13, x14
srl   x19, x18, x11
sra   x20, x4, x11
or    x21, x19, x20
and   x22, x21, x18

# ---------- S-type ----------
sw    x13, 0(x0)
sh    x14, 4(x0)
sb    x15, 8(x0)

# ---------- Branch coverage ----------
addi  x23, x0, 1
addi  x24, x0, 2

beq   x23, x23, BEQ_OK
  addi x31, x31, 1
BEQ_OK:

bne   x23, x24, BNE_OK
  addi x31, x31, 1
BNE_OK:

blt   x23, x24, BLT_OK
  addi x31, x31, 1
BLT_OK:

bge   x24, x23, BGE_OK
  addi x31, x31, 1
BGE_OK:

bltu  x23, x24, BLTU_OK
  addi x31, x31, 1
BLTU_OK:

bgeu  x24, x23, BGEU_OK
  addi x31, x31, 1
BGEU_OK:

# ---------- JAL + JALR coverage ----------
jal   x1, FUNC
addi  x31, x31, 1

DONE:
beq   x0, x0, DONE

FUNC:
  addi x25, x0, 5
  addi x26, x0, 6
  add  x27, x25, x26
  sw   x27, 12(x0)
  jalr x0, x1, 0
