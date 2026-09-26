#!/usr/bin/env python3
"""Prints the CHANGELOG section for one version (used as GitHub release notes)."""
import sys


def main():
    path, version = sys.argv[1], sys.argv[2]
    lines, on = [], False
    with open(path) as f:
        for line in f:
            if line.startswith("## "):
                if on:
                    break
                on = line[3:].split()[0] == version
                continue
            if on:
                lines.append(line)
    body = "".join(lines).strip() or f"Firewatch {version}"
    print(body)
    print()
    print("Firewatch by Baastik Labs")


if __name__ == "__main__":
    main()
