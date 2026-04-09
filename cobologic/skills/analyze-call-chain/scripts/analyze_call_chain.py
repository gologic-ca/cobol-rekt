#!/usr/bin/env python3
"""
Recursive COBOL call chain analyzer.
Usage: python analyze_call_chain.py --program MAINPROG [--max-depth 5] [--api-url http://localhost:8080]
"""
import argparse
import json
import sys
import httpx


def fetch_program(api_url: str, name: str) -> dict | None:
    try:
        with httpx.Client(base_url=api_url, timeout=15.0) as client:
            r = client.get(f"/api/programs/{name}")
            if r.status_code == 404:
                return None
            r.raise_for_status()
            return r.json()
    except Exception:
        return None


def build_tree(api_url: str, name: str, depth: int, max_depth: int, visited: set) -> dict:
    if depth > max_depth:
        return {"name": name, "depth": depth, "truncated": True, "children": []}
    if name in visited:
        return {"name": name, "depth": depth, "circular": True, "children": []}

    visited = visited | {name}
    data = fetch_program(api_url, name)

    if data is None:
        return {"name": name, "depth": depth, "external": True, "children": []}

    callees = data.get("callees", [])
    return {
        "name": name,
        "depth": depth,
        "calls_count": len(callees),
        "external": False,
        "children": [build_tree(api_url, c, depth + 1, max_depth, visited) for c in callees],
    }


def count_nodes(node: dict) -> int:
    return 1 + sum(count_nodes(c) for c in node.get("children", []))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--program", required=True)
    parser.add_argument("--max-depth", type=int, default=5)
    parser.add_argument("--api-url", default="http://localhost:8080")
    args = parser.parse_args()

    tree = build_tree(args.api_url, args.program, 0, args.max_depth, set())
    result = {
        "root_program": args.program,
        "max_depth_analyzed": args.max_depth,
        "total_programs_in_chain": count_nodes(tree),
        "call_tree": tree,
    }
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
