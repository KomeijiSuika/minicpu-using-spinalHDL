# RV32I S-type full coverage
# Covered: sb, sh, sw
# Includes necessary I/R instructions to build data and addresses.

addi gp, x0, 0          # base addr = 0
addi x1, x0, 0x11
slli x1, x1, 8
addi x1, x1, 0x22
slli x1, x1, 8
addi x1, x1, 0x33
slli x1, x1, 8
addi x1, x1, 0x44       # x1 = 0x11223344

addi x2, x0, -1
srli x2, x2, 1
xori x2, x2, 0x55       # x2 = 0x7fffffaa

addi x3, x0, 0x66
slli x3, x3, 8
addi x3, x3, 0x77       # x3 = 0x00006677

# sw coverage
sw   x1, 0(gp)
sw   x2, 4(gp)
sw   x3, 8(gp)

# sh coverage
sh   x1, 12(gp)
sh   x2, 16(gp)
sh   x3, 20(gp)

# sb coverage
sb   x1, 24(gp)
sb   x2, 28(gp)
sb   x3, 32(gp)

# more mixed stores
add  x4, x1, x3
sub  x5, x2, x1
xor  x6, x4, x5

sw   x4, 36(gp)
sh   x5, 40(gp)
sb   x6, 44(gp)

sw   x6, 48(gp)
sh   x4, 52(gp)
sb   x5, 56(gp)

# Stable tail
addi x7, x0, 0
addi x8, x0, 0
addi x9, x0, 0
addi x10, x0, 0
