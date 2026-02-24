# RV32I complex test (ALL EXCEPT U-type)
# Forbidden: lui, auipc
# Allowed: I, R, S, B, J, L

addi  x1,  x0, 64
addi  x2,  x0, 5
addi  x3,  x0, 9
addi  x4,  x0, -3
addi  x5,  x0, 12

# Build data region by stores
sw    x2,  0(x1)
sw    x3,  4(x1)
sh    x5,  8(x1)
sb    x4,  10(x1)

# Load/use chain
lw    x6,  0(x1)
lw    x7,  4(x1)
lh    x8,  8(x1)
lb    x9,  10(x1)
lbu   x10, 10(x1)

add   x11, x6, x7
sub   x12, x11, x8
xor   x13, x12, x10
or    x14, x13, x9
and   x15, x14, x11
slt   x16, x9,  x10
sltu  x17, x6,  x7
sll   x18, x11, x2
srl   x19, x18, x2
sra   x20, x12, x2

# Branch-heavy block
addi  x21, x0, 6
addi  x22, x0, 0
loop_a:
add   x22, x22, x21
addi  x21, x21, -1
bne   x21, x0, loop_a

# Conditional branch and skip
beq   x22, x0, bad_path
addi  x23, x22, 33
jal   x24, sub_func
j_after:
addi  x25, x24, -4
jal   x0, done

bad_path:
addi  x23, x0, -1

sub_func:
# use jalr return path
addi  x26, x0, 3
mul_like:
add   x23, x23, x26
addi  x26, x26, -1
bne   x26, x0, mul_like
jalr  x0, x24, 4

done:
# Final memory signature
sw    x22, 12(x1)
sw    x23, 16(x1)
sw    x25, 20(x1)
sh    x20, 24(x1)
sb    x16, 26(x1)

# extra load verify after stores
lw    x27, 12(x1)
lw    x28, 16(x1)
lw    x29, 20(x1)
lh    x30, 24(x1)
lbu   x31, 26(x1)
