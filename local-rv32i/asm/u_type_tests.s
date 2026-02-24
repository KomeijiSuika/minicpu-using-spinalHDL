.section .text
.option norvc
.globl _start
_start:
    # U-type instructions
    lui   x1, 0x12345       # Load upper immediate
    auipc x2, 0x1           # Add upper immediate to PC

    # I-type instructions
    addi  x3, x0, 10        # Immediate addition
    slli  x4, x3, 2         # Logical left shift immediate
    srli  x5, x3, 1         # Logical right shift immediate
    srai  x6, x3, 1         # Arithmetic right shift immediate
    andi  x7, x3, 0xF       # AND immediate
    ori   x8, x3, 0xF       # OR immediate
    xori  x9, x3, 0xF       # XOR immediate
    slti  x10, x3, 5        # Set less than immediate
    sltiu x11, x3, 5        # Set less than immediate unsigned

    # R-type instructions
    add   x12, x3, x4       # Addition
    sub   x13, x4, x3       # Subtraction
    sll   x14, x3, x4       # Logical left shift
    srl   x15, x4, x3       # Logical right shift
    sra   x16, x4, x3       # Arithmetic right shift
    and   x17, x3, x4       # AND
    or    x18, x3, x4       # OR
    xor   x19, x3, x4       # XOR
    slt   x20, x3, x4       # Set less than
    sltu  x21, x3, x4       # Set less than unsigned

    # S-type instructions (store operations)
    sw    x3, 0(x2)         # Store word
    sh    x4, 4(x2)         # Store halfword
    sb    x5, 6(x2)         # Store byte

    # Simple loop for testing
    addi  x22, x0, 10       # Loop counter
loop:
    addi  x22, x22, -1      # Decrement counter
    bne   x22, x0, loop     # Branch if not zero

    # End of program
    ret

.section .data
.align 2
out:
    .word 0,0,0,0,0,0,0,0,0,0
