#!/usr/bin/env python3

import argparse
import glob
import re
import shutil
import subprocess
import sys
from pathlib import Path


def parse_memh_words(memh_path: Path):
    image = {}
    word_index = 0
    for raw in memh_path.read_text(encoding="utf-8").splitlines():
        cut = raw.find("//")
        no_comment = (raw[:cut] if cut >= 0 else raw).strip()
        if not no_comment:
            continue
        if no_comment.startswith("@"):
            word_index = int(no_comment[1:].strip(), 16)
        else:
            token = no_comment.replace("_", "")
            image[word_index] = int(token, 16) & 0xFFFFFFFF
            word_index += 1
    return image


def parse_regfile_from_stdout(stdout: str):
    regs = [0] * 32
    found = set()
    pattern = re.compile(r"\bx(\d{2})\s*=\s*0x([0-9a-fA-F]+)")
    for line in stdout.splitlines():
        m = pattern.search(line)
        if not m:
            continue
        idx = int(m.group(1))
        value = int(m.group(2), 16) & 0xFFFFFFFF
        if 0 <= idx < 32:
            regs[idx] = value
            found.add(idx)

    if len(found) != 32:
        raise RuntimeError(
            f"Failed to parse full register file from pipelined-rv32i output (parsed {len(found)}/32 registers)."
        )
    return regs


def parse_data_mem_dump(mem_dump_path: Path, size_words: int = 1024):
    if not mem_dump_path.exists():
        raise RuntimeError(f"Missing data memory dump from pipelined-rv32i: {mem_dump_path}")
    image = parse_memh_words(mem_dump_path)
    return [(image.get(i, 0) & 0xFFFFFFFF) for i in range(size_words)]


def ensure_expect_testbench(pipelined_root: Path):
    tb_path = pipelined_root / "tests" / "test_expect_ref.sv"
    tb_text = """`timescale 1ns/1ps
`default_nettype none

module test_expect_ref;

`ifndef MAX_CYCLES
`define MAX_CYCLES 100
`endif

logic sysclk;
logic [1:0] buttons;
wire [1:0] leds;
wire [2:0] rgb;
wire [3:0] interface_mode;
wire backlight, display_rstb, data_commandb;
wire display_csb, spi_clk, spi_mosi;
logic spi_miso;
wire [7:0] gpio;

rv32i_system UUT(
  .sysclk(sysclk), .buttons(buttons), .leds(leds), .rgb(rgb),
  .interface_mode(interface_mode), .backlight(backlight),
  .display_rstb(display_rstb), .data_commandb(data_commandb),
  .display_csb(display_csb),
  .spi_mosi(spi_mosi), .spi_miso(spi_miso), .spi_clk(spi_clk),
  .gpio(gpio)
);

initial begin
  sysclk = 0;
  buttons = 2'b01;
  spi_miso = 0;
  repeat (2) @(negedge sysclk);
  buttons = 2'b00;
  repeat (`MAX_CYCLES) @(posedge sysclk);
  @(negedge sysclk);

  $display("Ran %d cycles, finishing.", `MAX_CYCLES);
  UUT.MMU.dump_memory("mmu");
    $display("x00 = 0x%08h", UUT.CORE.REGISTER_FILE.x00);
    $display("x01 = 0x%08h", UUT.CORE.REGISTER_FILE.x01);
    $display("x02 = 0x%08h", UUT.CORE.REGISTER_FILE.x02);
    $display("x03 = 0x%08h", UUT.CORE.REGISTER_FILE.x03);
    $display("x04 = 0x%08h", UUT.CORE.REGISTER_FILE.x04);
    $display("x05 = 0x%08h", UUT.CORE.REGISTER_FILE.x05);
    $display("x06 = 0x%08h", UUT.CORE.REGISTER_FILE.x06);
    $display("x07 = 0x%08h", UUT.CORE.REGISTER_FILE.x07);
    $display("x08 = 0x%08h", UUT.CORE.REGISTER_FILE.x08);
    $display("x09 = 0x%08h", UUT.CORE.REGISTER_FILE.x09);
    $display("x10 = 0x%08h", UUT.CORE.REGISTER_FILE.x10);
    $display("x11 = 0x%08h", UUT.CORE.REGISTER_FILE.x11);
    $display("x12 = 0x%08h", UUT.CORE.REGISTER_FILE.x12);
    $display("x13 = 0x%08h", UUT.CORE.REGISTER_FILE.x13);
    $display("x14 = 0x%08h", UUT.CORE.REGISTER_FILE.x14);
    $display("x15 = 0x%08h", UUT.CORE.REGISTER_FILE.x15);
    $display("x16 = 0x%08h", UUT.CORE.REGISTER_FILE.x16);
    $display("x17 = 0x%08h", UUT.CORE.REGISTER_FILE.x17);
    $display("x18 = 0x%08h", UUT.CORE.REGISTER_FILE.x18);
    $display("x19 = 0x%08h", UUT.CORE.REGISTER_FILE.x19);
    $display("x20 = 0x%08h", UUT.CORE.REGISTER_FILE.x20);
    $display("x21 = 0x%08h", UUT.CORE.REGISTER_FILE.x21);
    $display("x22 = 0x%08h", UUT.CORE.REGISTER_FILE.x22);
    $display("x23 = 0x%08h", UUT.CORE.REGISTER_FILE.x23);
    $display("x24 = 0x%08h", UUT.CORE.REGISTER_FILE.x24);
    $display("x25 = 0x%08h", UUT.CORE.REGISTER_FILE.x25);
    $display("x26 = 0x%08h", UUT.CORE.REGISTER_FILE.x26);
    $display("x27 = 0x%08h", UUT.CORE.REGISTER_FILE.x27);
    $display("x28 = 0x%08h", UUT.CORE.REGISTER_FILE.x28);
    $display("x29 = 0x%08h", UUT.CORE.REGISTER_FILE.x29);
    $display("x30 = 0x%08h", UUT.CORE.REGISTER_FILE.x30);
    $display("x31 = 0x%08h", UUT.CORE.REGISTER_FILE.x31);
  $finish;
end

always #5 sysclk = ~sysclk;

endmodule
"""
    tb_path.write_text(tb_text, encoding="utf-8")
    return tb_path


def build_verilator_cmd(tb_rel: str, init_mem_rel: str, cycles: int, binary_name: str):
    sources = [
        tb_rel,
        "hdl/register_file.sv",
        "hdl/register.sv",
        "hdl/register_neg.sv",
        "hdl/alu_behavioural.sv",
        "hdl/alu_types.sv",
        "hdl/mmu.sv",
        "hdl/block_ram.sv",
        "hdl/block_rom.sv",
        "hdl/memmap.sv",
        "hdl/distributed_ram.sv",
        "hdl/dual_port_distributed_ram.sv",
        "hdl/dual_port_ram.sv",
        "hdl/ili9341_display_peripheral.sv",
        "hdl/ili9341_defines.sv",
        "hdl/spi_controller.sv",
        "hdl/spi_types.sv",
        "hdl/pwm.sv",
        "hdl/pulse_generator.sv",
        "hdl/rv32i_defines.sv",
        "hdl/rv32i_pipelined_core.sv",
        "hdl/rv32i_system.sv",
    ]
    cmd = [
        "verilator",
        "--binary",
        "--timing",
        "-Wno-fatal",
        "-D__ICARUS__",
        f"-DINITIAL_INST_MEM=\"{init_mem_rel}\"",
        f"-DMAX_CYCLES={cycles}",
        "-Ihdl",
        "-Itests",
        "--top-module",
        "test_expect_ref",
        "-o",
        binary_name,
    ]
    cmd.extend(sources)
    return cmd


def build_reference_simulator(workspace: Path, cycles: int):
    pipelined_root = workspace / "pipelined-rv32i"
    if not pipelined_root.exists():
        raise RuntimeError(f"Missing reference repo directory: {pipelined_root}")

    tb_path = ensure_expect_testbench(pipelined_root)

    binary_name = "sim_expect_ref"
    build_cmd = build_verilator_cmd(
        tb_rel=str(tb_path.relative_to(pipelined_root)),
        init_mem_rel="asm/__expect_input.memh",
        cycles=cycles,
        binary_name=binary_name,
    )

    print("[generate_expected] Building reference simulator:")
    print("  " + " ".join(build_cmd))
    build = subprocess.run(build_cmd, cwd=str(pipelined_root), capture_output=True, text=True)
    if build.returncode != 0:
        raise RuntimeError(
            "Verilator build failed for pipelined-rv32i.\n"
            f"stdout:\n{build.stdout}\n"
            f"stderr:\n{build.stderr}"
        )

    exe_path = pipelined_root / "obj_dir" / binary_name
    if not exe_path.exists():
        raise RuntimeError(f"Expected simulator binary not found: {exe_path}")

    return pipelined_root, exe_path


def run_pipelined_reference(pipelined_root: Path, exe_path: Path, memh_path: Path):
    staged_memh = pipelined_root / "asm" / "__expect_input.memh"
    shutil.copyfile(memh_path, staged_memh)

    for stale in ["mmu_data.out", "mmu_inst.out", "mmu_vram.out", "rv32i_system.fst"]:
        stale_path = pipelined_root / stale
        if stale_path.exists():
            stale_path.unlink()

    print(f"[generate_expected] Running reference simulator: {exe_path}")
    run = subprocess.run([str(exe_path)], cwd=str(pipelined_root), capture_output=True, text=True)
    if run.returncode != 0:
        raise RuntimeError(
            "Reference simulator run failed.\n"
            f"stdout:\n{run.stdout}\n"
            f"stderr:\n{run.stderr}"
        )

    regs = parse_regfile_from_stdout(run.stdout)
    data_mem = parse_data_mem_dump(pipelined_root / "mmu_data.out", size_words=1024)
    return regs, data_mem


def write_expected(pipelined_root: Path, exe_path: Path, memh: Path, expected_dir: Path):
    regs, data_mem = run_pipelined_reference(pipelined_root, exe_path, memh)
    program = memh.stem
    expected_dir.mkdir(parents=True, exist_ok=True)

    reg_path = expected_dir / f"regfile_{program}.txt"
    mem_path = expected_dir / f"datamem_{program}.txt"

    reg_lines = [f"x{i:02d} = 0x{regs[i] & 0xFFFFFFFF:08x}" for i in range(32)]
    mem_lines = [f"0x{(i*4):08x} : 0x{data_mem[i] & 0xFFFFFFFF:08x}" for i in range(1024)]

    reg_path.write_text("\n".join(reg_lines) + "\n", encoding="utf-8")
    mem_path.write_text("\n".join(mem_lines) + "\n", encoding="utf-8")

    print(f"[generate_expected] expected written: {reg_path}")
    print(f"[generate_expected] expected written: {mem_path}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate expected regfile/datamem from .memh via pipelined-rv32i SystemVerilog CPU simulation.")
    parser.add_argument("--workspace", default=".", help="Workspace root (default: current directory)")
    parser.add_argument("--memh", help="Single .memh file path (relative to workspace)")
    parser.add_argument("--all", action="store_true", help="Use all local-rv32i/asm/*.memh")
    parser.add_argument("--cycles", type=int, default=300, help="Reference simulation cycles passed to testbench MAX_CYCLES (default: 300)")
    parser.add_argument("--expected-dir", default="local-rv32i/expected", help="Expected dir (default: local-rv32i/expected)")
    args = parser.parse_args()

    workspace = Path(args.workspace).resolve()
    expected_dir = (workspace / args.expected_dir).resolve()

    targets = []
    if args.all:
        targets = [Path(p).resolve() for p in sorted(glob.glob(str(workspace / "local-rv32i" / "asm" / "*.memh")))]
        if not targets:
            print("[generate_expected] ERROR: no .memh files found. Run assemble_memh.py first.")
            return 1
    elif args.memh:
        m = (workspace / args.memh).resolve()
        if not m.exists() or m.suffix != ".memh":
            print(f"[generate_expected] ERROR: invalid --memh: {m}")
            return 1
        targets = [m]
    else:
        print("[generate_expected] ERROR: provide --all or --memh <file.memh>")
        return 1

    fail = 0
    try:
        pipelined_root, exe_path = build_reference_simulator(workspace, args.cycles)
    except Exception as e:
        print(f"[generate_expected] ERROR: failed to build reference simulator -> {e}")
        return 1

    for memh in targets:
        try:
            write_expected(pipelined_root, exe_path, memh, expected_dir)
        except Exception as e:
            fail += 1
            print(f"[generate_expected] FAIL: {memh} -> {e}")

    if fail:
        print(f"[generate_expected] Completed with failures: {fail}")
        return 1

    print(f"[generate_expected] Completed successfully: {len(targets)} programs")
    return 0


if __name__ == "__main__":
    sys.exit(main())
