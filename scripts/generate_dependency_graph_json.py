#!/usr/bin/env python3
"""
Generate dependency graph from CBL-JCL mappings and AST files.
Creates a JSON-based dependency graph without external dependencies.
"""

import json
import sys
import argparse
from pathlib import Path
from datetime import datetime

def generate_dependency_graph(cobol_dir, jcl_dir, ast_dir, output_file):
    """Generate dependency graph from mappings and AST files."""
    
    # Find project-analysis.json (the mappings file)
    project_root = Path(cobol_dir).parent.parent
    mappings_file = project_root / "project-analysis.json"
    
    if not mappings_file.exists():
        # Try alternate location
        mappings_file = Path(ast_dir).parent / "project-analysis.json"
    
    if not mappings_file.exists():
        print(f"[ERROR] Mappings file not found: {mappings_file}")
        return False
    
    # Read mappings
    try:
        with open(mappings_file, 'r') as f:
            mappings = json.load(f)
    except Exception as e:
        print(f"[ERROR] Failed to read mappings: {e}")
        return False
    
    # Build dependency graph
    graph = {
        "generatedAt": datetime.now().isoformat(),
        "statistics": mappings.get("statistics", {}),
        "nodes": [],
        "edges": []
    }
    
    # Create nodes for programs
    programs_with_jcl = []
    programs_without_jcl = []
    
    for program_info in mappings.get("cbl_files", []):
        program_name = program_info.get("program")
        jcl_files = program_info.get("jcl_files", [])
        
        # Add program node
        node = {
            "id": program_name,
            "type": "program",
            "label": program_name,
            "jcl_files": jcl_files
        }
        graph["nodes"].append(node)
        
        if jcl_files:
            programs_with_jcl.append(program_name)
            
            # Add JCL nodes and edges
            for jcl_file in jcl_files:
                jcl_id = jcl_file.replace(".jcl", "").replace(".", "_")
                
                # Add JCL node if not already added
                if not any(n["id"] == jcl_id for n in graph["nodes"]):
                    graph["nodes"].append({
                        "id": jcl_id,
                        "type": "jcl",
                        "label": jcl_file
                    })
                
                # Add edge from JCL to program
                graph["edges"].append({
                    "source": jcl_id,
                    "target": program_name,
                    "relationship": "executes"
                })
        else:
            programs_without_jcl.append(program_name)
    
    # Add statistics to graph
    graph["summary"] = {
        "total_programs": len(mappings.get("cbl_files", [])),
        "programs_with_jcl": len(programs_with_jcl),
        "programs_without_jcl": len(programs_without_jcl),
        "total_jcl_files": len(set(
            jcl for prog in mappings.get("cbl_files", [])
            for jcl in prog.get("jcl_files", [])
        ))
    }
    
    # Write graph file
    try:
        with open(output_file, 'w') as f:
            json.dump(graph, f, indent=2)
        print(f"[SUCCESS] Dependency graph created: {output_file}")
        print(f"  - Nodes: {len(graph['nodes'])}")
        print(f"  - Edges: {len(graph['edges'])}")
        return True
    except Exception as e:
        print(f"[ERROR] Failed to write graph: {e}")
        return False

def main():
    parser = argparse.ArgumentParser(
        description='Generate dependency graph from CBL-JCL mappings'
    )
    parser.add_argument('--cobol-dir', required=True, help='COBOL source directory')
    parser.add_argument('--jcl-dir', required=True, help='JCL directory')
    parser.add_argument('--ast-dir', required=True, help='AST output directory')
    parser.add_argument('--output', required=True, help='Output graph file')
    
    args = parser.parse_args()
    
    success = generate_dependency_graph(
        args.cobol_dir,
        args.jcl_dir,
        args.ast_dir,
        args.output
    )
    
    return 0 if success else 1

if __name__ == '__main__':
    sys.exit(main())
