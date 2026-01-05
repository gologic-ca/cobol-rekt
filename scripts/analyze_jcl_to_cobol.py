#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Analyse JCL → COBOL: Partir des JCL pour tracer les fichiers COBOL
GÉNÉRIQUE: Scanne récursivement tous les répertoires pour JCL et COBOL
"""

import os
import re
import sys
import argparse
from pathlib import Path
from collections import defaultdict

# Force UTF-8 output on Windows
if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

try:
    import graphviz
except ImportError:
    print("[ERROR] graphviz not installed: pip install graphviz")
    exit(1)


def extract_pgm_from_jcl(jcl_content):
    """Extract all PGM= values from JCL content"""
    pattern = r'PGM=([A-Z0-9]+)'
    matches = re.findall(pattern, jcl_content)
    return list(set(matches))


def is_system_program(prog_name):
    """Check if program is a system program"""
    system_programs = {
        'IEFBR14', 'IDCAMS', 'SORT', 'SDSF', 'IEBGENER', 
        'IKJEFT01', 'IKJEFT1B', 'FTP', 'DFHCSDUP'
    }
    return prog_name in system_programs


def find_jcl_files(root_dir):
    """Recursively find all JCL files in directory tree"""
    jcl_files = []
    for path in Path(root_dir).rglob('*'):
        if path.is_file() and path.suffix.upper() in ['.JCL']:
            jcl_files.append(path)
    return sorted(jcl_files)


def find_cobol_files(root_dir):
    """Recursively find all COBOL files in directory tree and index by name"""
    cobol_index = {}  # { 'PROGNAME': Path }
    for path in Path(root_dir).rglob('*.cbl'):
        if path.is_file():
            prog_name = path.stem.upper()  # Get filename without extension
            cobol_index[prog_name] = path
    return cobol_index


def main():
    parser = argparse.ArgumentParser(
        description='Analyze JCL → COBOL relationships (recursive)'
    )
    parser.add_argument(
        '-j', '--jcl-dir',
        required=True,
        help='Root directory to scan for JCL files (recursive)'
    )
    parser.add_argument(
        '-c', '--cobol-dir',
        required=True,
        help='Root directory to scan for COBOL files (recursive)'
    )
    parser.add_argument(
        '-o', '--output',
        default='out/report/JCL_to_COBOL_mapping',
        help='Output file path for SVG graph (default: out/report/JCL_to_COBOL_mapping)'
    )
    parser.add_argument(
        '--json',
        action='store_true',
        help='Output JSON format instead of SVG graph'
    )
    
    args = parser.parse_args()
    
    jcl_root = Path(args.jcl_dir)
    cbl_root = Path(args.cobol_dir)
    output_file = args.output
    
    # Validate directories exist
    if not jcl_root.exists():
        print(f"[ERROR] JCL directory not found: {jcl_root}", file=sys.stderr)
        sys.exit(1)
    if not cbl_root.exists():
        print(f"[ERROR] COBOL directory not found: {cbl_root}", file=sys.stderr)
        sys.exit(1)
    
    # Only print diagnostic info if NOT outputting JSON
    if not args.json:
        print(f"[SCAN] Analyzing JCL → COBOL mappings")
        print(f"       JCL root: {jcl_root}")
        print(f"       COBOL root: {cbl_root}")
        print()
    
    # Find all JCL and COBOL files recursively
    if not args.json:
        print("[INDEX] Building COBOL index...")
    cobol_index = find_cobol_files(cbl_root)
    if not args.json:
        print(f"        Found {len(cobol_index)} COBOL files")
    
    if not args.json:
        print("[SCAN] Finding JCL files...")
    jcl_files = find_jcl_files(jcl_root)
    if not args.json:
        print(f"       Found {len(jcl_files)} JCL files")
        print()
    
    # Data structures
    jcl_to_cobol = defaultdict(list)  # JCL → [COBOL programs]
    cobol_to_jcl = defaultdict(list)  # COBOL → [JCL files]
    stats = {'found': 0, 'system': 0, 'missing': 0}
    
    # Scan JCL files
    for jcl_file in jcl_files:
        with open(jcl_file, 'r', encoding='utf-8', errors='ignore') as f:
            jcl_content = f.read()
        
        programs = extract_pgm_from_jcl(jcl_content)
        
        if not programs:
            continue
        
        # Get relative path for display
        try:
            jcl_name = jcl_file.relative_to(jcl_root)
        except ValueError:
            jcl_name = jcl_file.name
        
        if not args.json:
            print(f"[JCL] {jcl_name}")
        
        for prog in programs:
            prog_upper = prog.upper()
            
            if prog_upper in cobol_index:
                cbl_file = cobol_index[prog_upper]
                if not args.json:
                    print(f"  ✅ {cbl_file.name}")
                jcl_to_cobol[str(jcl_name)].append(prog_upper)
                cobol_to_jcl[prog_upper].append(str(jcl_name))
                stats['found'] += 1
            elif is_system_program(prog_upper):
                if not args.json:
                    print(f"  ⚙️  {prog_upper} (system)")
                stats['system'] += 1
            else:
                if not args.json:
                    print(f"  ❌ {prog_upper}")
                stats['missing'] += 1
        
        if not args.json:
            print()
    
    # Output as JSON if requested
    if args.json:
        import json
        output_data = {
            "statistics": stats,
            "cbl_files": [
                {
                    "program": prog,
                    "jcl_files": cobol_to_jcl[prog]
                }
                for prog in sorted(cobol_to_jcl.keys())
            ]
        }
        print(json.dumps(output_data, indent=2))
        return
    
    # Print summary only if not JSON output
    if not args.json:
        print("=" * 60)
        print("SUMMARY: JCL → COBOL")
        print("=" * 60)
        print(f"Programs found: {stats['found']}")
        print(f"System programs: {stats['system']}")
        print(f"Missing programs: {stats['missing']}")
        print()
        print("COBOL Programs with JCL associations:")
        print()
        
        for prog in sorted(cobol_to_jcl.keys()):
            jcl_list = ', '.join(cobol_to_jcl[prog])
            print(f"  {prog}.cbl ← {jcl_list}")
        
        # Generate graph
        print()
        print("[GRAPH] Generating dependency graph...")
    
    if not args.json:
        graph = graphviz.Digraph(
            name='JCL_to_COBOL',
            comment='JCL to COBOL Dependency Mapping',
            format='svg',
            engine='dot'
        )
        
        graph.attr(rankdir='LR')
        graph.attr('node', shape='box', style='rounded,filled', fontname='Arial', fontsize='10')
        
        # Add JCL nodes
        with graph.subgraph(name='cluster_jcl') as jcl_cluster:
            jcl_cluster.attr(label='JCL Files', style='filled', color='lightblue')
            jcl_cluster.attr('node', fillcolor='#AADDFF', shape='note')
            for jcl in sorted(jcl_to_cobol.keys()):
                jcl_cluster.node(f"JCL_{jcl}", jcl.replace('.jcl', '').replace('.JCL', ''))
        
        # Add COBOL nodes
        with graph.subgraph(name='cluster_cobol') as cbl_cluster:
            cbl_cluster.attr(label='COBOL Programs', style='filled', color='lightgreen')
            cbl_cluster.attr('node', fillcolor='#AAFFAA', shape='component')
            for prog in sorted(cobol_to_jcl.keys()):
                cbl_cluster.node(f"CBL_{prog}", prog)
        
        # Add edges
        for jcl, programs in jcl_to_cobol.items():
            jcl_node = f"JCL_{jcl}"
            for prog in programs:
                cbl_node = f"CBL_{prog}"
                graph.edge(jcl_node, cbl_node, label='executes')
        
        # Render
        output_path = Path(output_file)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        
        graph.render(str(output_path), cleanup=True)
        
        print(f"[OK] Graph generated: {output_path}.svg")

if __name__ == '__main__':
    main()
