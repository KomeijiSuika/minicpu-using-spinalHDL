# RV32I mixed coverage without load instructions
# Covered:
#   U:    lui, auipc
#   I:    addi, slli, slti, sltiu, xori, srli, srai, ori, andi, jalr
#   R:    add, sub, sll, slt, sltu, xor, srl, sra, or, and
#   S:    sb, sh, sw
#   B:    beq, bne, blt, bge, bltu, bgeu
#   J:    jal
# Forbidden:
#   lb, lh, lw, lbu, lhu
#
# Notes:
# - Instruction types are intentionally interleaved to stress forwarding/stall logic.
# - Stores target DATA bank base 0x3000_0000 (addr[31:28] = 0x3) so DataMem will respond.

# DATA bank base = 0x3000_0000
lui   x28, 0x30000

# Seed values (I-type) and immediate dataflow
addi  x3,  x0, 17
slli  x5,  x3, 3
srli  x6,  x5, 2
xori  x8,  x5, 0x155

# U-type mixed in
lui   x1, 0x12345
auipc x2, 0

# R-type + store effects
add   x13, x3, x5
sw    x13, 0(x28)

# More I-type + shifts
addi  x4,  x0, -9
srai  x7,  x4, 1
slti  x11, x4, -1

# R-type depending on x11
sll   x15, x3, x11
sb    x15, 8(x28)

sub   x14, x13, x6
sh    x14, 4(x28)

# Logic mix
ori   x9,  x8, 0x22
andi  x10, x9, 0x1ff

xor   x18, x13, x14
srl   x19, x18, x11
sra   x20, x4,  x11
or    x21, x19, x20
and   x22, x21, x18

# Comparisons
sltiu x12, x4, 32
slt   x16, x4, x3
sltu  x17, x4, x3

# ---------- Branch coverage (with extra mixed ALU ops between blocks) ----------
addi  x23, x0, 1
addi  x24, x0, 2

beq   x23, x23, BEQ_OK
  addi x31, x31, 1
BEQ_OK:
add   x13, x13, x23

bne   x23, x24, BNE_OK
  addi x31, x31, 1
BNE_OK:
sub   x14, x14, x24

blt   x23, x24, BLT_OK
  addi x31, x31, 1
BLT_OK:
xor   x18, x18, x13

bge   x24, x23, BGE_OK
  addi x31, x31, 1
BGE_OK:
or    x21, x21, x14

bltu  x23, x24, BLTU_OK
  addi x31, x31, 1
BLTU_OK:
and   x22, x22, x18

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
  sw   x27, 12(x28)
  jalr x0, x1, 0
