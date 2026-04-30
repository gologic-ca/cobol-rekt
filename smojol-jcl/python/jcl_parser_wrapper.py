#!/usr/bin/env python3
"""
JCL Parser Wrapper
Wraps the local jcl_parser-gologic library to parse JCL files and output JSON.
"""
import sys
import json
from pathlib import Path
from jcl_parser import JCLParser
from jcl_preprocessing import preprocess_jcl_continuations, fix_jcl_parameters_in_result


def _detect_plan_directory(jcl_file_path: str) -> str:
    """
    Auto-detect the plan directory relative to the JCL file.
    Looks for a 'plan' folder at the same level or parent level as the JCL directory.
    """
    jcl_path = Path(jcl_file_path)
    jcl_parent = jcl_path.parent
    
    # Try: sibling of JCL parent (e.g., project/plan/ when JCL is in project/jcl/)
    potential = jcl_parent.parent / "plan"
    if potential.exists():
        return str(potential)
    
    # Try: inside JCL directory (e.g., jcl/plan/)
    potential = jcl_parent / "plan"
    if potential.exists():
        return str(potential)
    
    return None


def _enrich_steps_with_bindplans(jcl_dict: dict) -> dict:
    """
    Enrich BINDPLAN steps with PGM (from entry_point) and obj_libs (from include_files).
    Correlates the 'bindplans' section with matching steps by name and line number.
    """
    bindplans = jcl_dict.get('bindplans', [])
    steps = jcl_dict.get('steps', [])
    
    if not bindplans or not steps:
        return jcl_dict
    
    # Index bindplans by (name, line) for correlation with steps
    bp_by_name_line = {}
    for bp in bindplans:
        key = (bp.get('name', ''), bp.get('line', 0))
        bp_by_name_line[key] = bp
    
    # Enrich matching steps
    for step in steps:
        params = step.get('parameters', {})
        if not isinstance(params, dict):
            continue
        
        # Detect BINDPLAN steps (have BINDPLAN in parameters)
        if 'BINDPLAN' not in params:
            continue
        
        # Find matching bindplan by name and line
        step_key = (step.get('name', ''), step.get('line', 0))
        bp = bp_by_name_line.get(step_key)
        
        if bp:
            # Add PGM from entry_point (makes it look like a program execution)
            entry_point = bp.get('entry_point')
            if entry_point:
                params['PGM'] = entry_point
            
            # Add obj_libs from include_files
            include_files = bp.get('include_files', [])
            if include_files:
                step['obj_libs'] = include_files
    
    return jcl_dict


def parse_jcl_file(jcl_file_path: str, plan_base_path: str = None) -> dict:
    """
    Parse a JCL file and return the parsed structure as a dictionary.
    
    Args:
        jcl_file_path: Path to the JCL file to parse
        plan_base_path: Optional explicit path to the plan directory for BINDPLAN resolution
        
    Returns:
        Dictionary containing the parsed JCL structure
    """
    try:
        parser = JCLParser()
        
        # Read the JCL file
        with open(jcl_file_path, 'r', encoding='utf-8') as f:
            jcl_content = f.read()
        
        # Preprocess JCL to handle multi-line continuations
        jcl_content = preprocess_jcl_continuations(jcl_content)
        
        # Use explicit plan_base_path if provided, otherwise auto-detect
        base_path = plan_base_path or _detect_plan_directory(jcl_file_path)
        
        # Parse the JCL (with base_path for BINDPLAN plan file resolution)
        if base_path:
            parsed_jcl = parser.parse_string(jcl_content, base_path=base_path)
        else:
            parsed_jcl = parser.parse_string(jcl_content)
        
        # Convert to dict if needed
        jcl_dict = parsed_jcl.to_json() if hasattr(parsed_jcl, 'to_json') else parsed_jcl
        
        # Fix parameter parsing (generic solution for parentheses, quotes, etc.)
        jcl_dict = fix_jcl_parameters_in_result(jcl_dict)
        
        # Enrich BINDPLAN steps with PGM and obj_libs from plan file data
        jcl_dict = _enrich_steps_with_bindplans(jcl_dict)
        
        return {
            "status": "success",
            "file": jcl_file_path,
            "jcl": jcl_dict
        }
        
    except FileNotFoundError:
        return {
            "status": "error",
            "error": "FILE_NOT_FOUND",
            "message": f"JCL file not found: {jcl_file_path}"
        }
    except Exception as e:
        return {
            "status": "error",
            "error": "PARSING_ERROR",
            "message": str(e),
            "type": type(e).__name__
        }


def main():
    """Main entry point for the JCL parser wrapper."""
    if len(sys.argv) < 2:
        error_result = {
            "status": "error",
            "error": "INVALID_ARGUMENTS",
            "message": "Usage: python jcl_parser_wrapper.py <jcl_file_path> [plan_base_path]"
        }
        print(json.dumps(error_result, indent=2))
        sys.exit(1)
    
    jcl_file_path = sys.argv[1]
    plan_base_path = sys.argv[2] if len(sys.argv) > 2 else None
    result = parse_jcl_file(jcl_file_path, plan_base_path=plan_base_path)
    
    # Output JSON to stdout
    print(json.dumps(result, indent=2))
    
    # Exit with appropriate code
    sys.exit(0 if result["status"] == "success" else 1)


if __name__ == "__main__":
    main()
