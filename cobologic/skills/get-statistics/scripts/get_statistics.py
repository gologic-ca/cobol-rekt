#!/usr/bin/env python3
"""
COBOL statistics aggregator.
Usage: python get_statistics.py [--api-url http://localhost:8080]
"""
import argparse
import json
import httpx


def fetch(api_url: str, endpoint: str) -> list:
    try:
        with httpx.Client(base_url=api_url, timeout=30.0) as client:
            r = client.get(endpoint)
            r.raise_for_status()
            result = r.json()
            return result if isinstance(result, list) else []
    except Exception:
        return []


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-url", default="http://localhost:8080")
    args = parser.parse_args()

    programs = fetch(args.api_url, "/api/programs")
    copybooks = fetch(args.api_url, "/api/copybooks")
    jcls = fetch(args.api_url, "/api/jcls")
    datasets = fetch(args.api_url, "/api/datasets")

    program_complexity = sorted(
        [
            {
                "name": p.get("name"),
                "complexity_score": len(p.get("copybooks", [])) * 2 + len(p.get("callees", [])) * 3 + len(p.get("callers", [])),
                "copybooks": len(p.get("copybooks", [])),
                "calls": len(p.get("callees", [])),
            }
            for p in programs
        ],
        key=lambda x: x["complexity_score"],
        reverse=True,
    )

    copybook_usage = sorted(
        [{"name": c.get("name"), "used_by_count": len(c.get("usedBy", []))} for c in copybooks],
        key=lambda x: x["used_by_count"],
        reverse=True,
    )

    result = {
        "totals": {"programs": len(programs), "copybooks": len(copybooks), "jcls": len(jcls), "datasets": len(datasets)},
        "top_programs": program_complexity[:10],
        "top_copybooks": copybook_usage[:10],
        "complexity_analysis": {
            "high_complexity_count": sum(1 for p in program_complexity if p["complexity_score"] > 30),
            "medium_complexity_count": sum(1 for p in program_complexity if 15 < p["complexity_score"] <= 30),
            "low_complexity_count": sum(1 for p in program_complexity if p["complexity_score"] <= 15),
        },
    }
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
