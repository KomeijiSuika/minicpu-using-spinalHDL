.section .text
.option norvc
.globl _start
_start:
    # I-type initial values
    addi x5, x0, 7        # t0 = 7
    addi x6, x0, -3       # t1 = -3

    # R-type arithmetic
    add  x7,  x5, x6      # t2 = t0 + t1
    sub  x8,  x5, x6      # t3 = t0 - t1

    # U-type instructions
    lui   x9,  0x12345    # t4 = 0x12345000
    auipc x10, 0xa        # t5 = PC + (0xa << 12)

    # I-type shifts and R-type shifts
    slli x11, x5, 4       # t6 = t0 << 4 (I-type)
    sll  x12, x5, x6      # t7 = t0 << (t1 & 0x1f) (R-type)

    srli x13, x5, 1       # t8 = t0 >> 1 (logical, I-type)
    srl  x14, x5, x6      # t9 = t0 >> (t1 & 0x1f) (logical, R-type)
    srai x15, x5, 1       # t10 = t0 >> 1 (arith, I-type)
    sra  x16, x5, x6      # t11 = t0 >> (t1 & 0x1f) (arith, R-type)

    # Logical ops (R-type and I-type)
    and  x17, x5, x6      # t12 = t0 & t1
    andi x18, x5, 0xF     # t13 = t0 & 0xF
    or   x19, x5, x6      # t14 = t0 | t1
    ori  x20, x5, 0xF     # t15 = t0 | 0xF
    xor  x21, x5, x6      # t16 = t0 ^ t1
    xori x22, x5, 0xF     # t17 = t0 ^ 0xF

    # Set-less-than variants
    slt  x23, x5, x6      # t18 = (t0 < t1) signed
    slti x24, x5, 1       # t19 = (t0 < 1) signed (I-type)
    sltu x25, x5, x6      # t20 = (t0 < t1) unsigned
    sltiu x26, x5, 1      # t21 = (t0 < 1) unsigned (I-type)

    # Prepare data pointer using PC-relative addressing
    auipc x2, %pcrel_hi(out)
    addi  x2, x2, %pcrel_lo(out)

    # Store test results to data region (word-per-register)
    sw x5,  0(x2)
    sw x6,  4(x2)
    sw x7,  8(x2)
    sw x8,  12(x2)
    sw x9,  16(x2)
    sw x10, 20(x2)
    sw x11, 24(x2)
    sw x12, 28(x2)
    sw x13, 32(x2)
    sw x14, 36(x2)
    sw x15, 40(x2)
    sw x16, 44(x2)
    sw x17, 48(x2)
    sw x18, 52(x2)
    sw x19, 56(x2)
    sw x20, 60(x2)
    sw x21, 64(x2)
    sw x22, 68(x2)
    sw x23, 72(x2)
    sw x24, 76(x2)
    sw x25, 80(x2)
    sw x26, 84(x2)

1:  j 1b

    .section .data
    .align 2
out:
    .word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
