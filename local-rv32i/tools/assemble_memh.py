#!/usr/bin/env python3

import argparse
import glob
import subprocess
import sys
from pathlib import Path


def run(cmd, cwd: Path) -> int:
    print("[assemble_memh] $", " ".join(cmd))
    p = subprocess.run(cmd, cwd=str(cwd))
    return p.returncode


def main() -> int:
    parser = argparse.ArgumentParser(description="Assemble local-rv32i .s files into .memh files.")
    parser.add_argument("--workspace", default=".", help="Workspace root (default: current directory)")
    parser.add_argument("--asm", help="Single .s file path (relative to workspace)")
    parser.add_argument("--all", action="store_true", help="Assemble all local-rv32i/asm/*.s")
    parser.add_argument("--out", help="Output .memh path for --asm mode")
    args = parser.parse_args()

    workspace = Path(args.workspace).resolve()
    # Prefer local-rv32i/assembler.py if present; otherwise fall back to the
    # reference repo copy at pipelined-rv32i/assembler.py.
    assembler_candidates = [
        workspace / "local-rv32i" / "assembler.py",
        workspace / "pipelined-rv32i" / "assembler.py",
    ]
    assembler = next((p for p in assembler_candidates if p.exists()), None)
    if assembler is None:
        print("[assemble_memh] ERROR: assembler.py not found. Tried:")
        for p in assembler_candidates:
            print(f"  - {p}")
        return 1

    targets = []
    if args.all:
        for s in sorted(glob.glob(str(workspace / "local-rv32i" / "asm" / "*.s"))):
            s_path = Path(s)
            m_path = s_path.with_suffix(".memh")
            targets.append((s_path, m_path))
    elif args.asm:
        s_path = (workspace / args.asm).resolve()
        if not s_path.exists() or s_path.suffix != ".s":
            print(f"[assemble_memh] ERROR: invalid --asm: {s_path}")
            return 1
        if args.out:
            m_path = (workspace / args.out).resolve()
        else:
            m_path = s_path.with_suffix(".memh")
        targets.append((s_path, m_path))
    else:
        print("[assemble_memh] ERROR: provide --all or --asm <file.s>")
        return 1

    fail = 0
    for s_path, m_path in targets:
        m_path.parent.mkdir(parents=True, exist_ok=True)
        cmd = [
            "python3",
            str(assembler),
            str(s_path.relative_to(workspace)),
            "-o",
            str(m_path.relative_to(workspace)),
        ]
        code = run(cmd, workspace)
        if code != 0:
            fail += 1
            print(f"[assemble_memh] FAIL: {s_path}")
        else:
            print(f"[assemble_memh] OK  : {s_path} -> {m_path}")

    if fail > 0:
        print(f"[assemble_memh] Completed with failures: {fail}")
        return 1
    print(f"[assemble_memh] Completed successfully: {len(targets)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
