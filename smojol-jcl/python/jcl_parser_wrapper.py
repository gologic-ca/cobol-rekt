#!/usr/bin/env python3
"""
JCL Parser Wrapper
Wraps the legacylens-jcl-parser library to parse JCL files and output JSON.
"""
import sys
import json
from pathlib import Path
from jcl_parser import JCLParser


def parse_jcl_file(jcl_file_path: str) -> dict:
    """
    Parse a JCL file and return the parsed structure as a dictionary.
    
    Args:
        jcl_file_path: Path to the JCL file to parse
        
    Returns:
        Dictionary containing the parsed JCL structure
    """
    try:
        parser = JCLParser()
        
        # Read the JCL file
        with open(jcl_file_path, 'r', encoding='utf-8') as f:
            jcl_content = f.read()
        
        # Parse the JCL
        parsed_jcl = parser.parse_string(jcl_content)
        
        return {
            "status": "success",
            "file": jcl_file_path,
            "jcl": parsed_jcl.to_json() if hasattr(parsed_jcl, 'to_json') else parsed_jcl
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
            "message": "Usage: python jcl_parser_wrapper.py <jcl_file_path>"
        }
        print(json.dumps(error_result, indent=2))
        sys.exit(1)
    
    jcl_file_path = sys.argv[1]
    result = parse_jcl_file(jcl_file_path)
    
    # Output JSON to stdout
    print(json.dumps(result, indent=2))
    
    # Exit with appropriate code
    sys.exit(0 if result["status"] == "success" else 1)


if __name__ == "__main__":
    main()
