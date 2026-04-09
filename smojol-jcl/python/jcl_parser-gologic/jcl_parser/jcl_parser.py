#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
JCL Parser - Parse JCL files and extract information in JSON format
"""
import json
import os
from typing import Dict, List, Any, Optional
import re

from jcl_parser.extractors.job_extractor import extract_jobs
from jcl_parser.extractors.step_extractor import extract_steps
from jcl_parser.extractors.proc_extractor import extract_procedures
from jcl_parser.extractors.comment_extractor import extract_comments
from jcl_parser.extractors.bindplan_extractor import extract_bindplans


class JCLParser:
    """Parser for JCL (Job Control Language) files"""
    
    def parse_file(self, file_path: str, search_plan_files: bool = True) -> Dict[str, Any]:
        """Parse a JCL file and return structured data
        
        Args:
            file_path: Path to the JCL file to parse.
            search_plan_files: If True, attempts to find and parse associated plan files.
            
        Returns:
            Dictionary with parsed JCL data.
        """
        with open(file_path, 'r') as file:
            jcl_content = file.read()
        
        # Determine base path for searching plan files
        base_path = os.path.dirname(os.path.abspath(file_path)) if search_plan_files else None
        
        return self.parse_string(jcl_content, base_path=base_path)
    
    def parse_string(self, jcl_content: str, base_path: Optional[str] = None) -> Dict[str, Any]:
        """Parse JCL content from a string and return structured data"""
        # Create the basic structure
        jcl_data = {
            "job": {},
            "steps": [],
            "procedures": [],
            "comments": [],
            "bindplans": []
        }
        
        # Extract job information (take the first job found)
        jobs = extract_jobs(jcl_content)
        if jobs:
            jcl_data["job"] = jobs[0]
        
        # Extract procedures and their steps
        jcl_data["procedures"] = extract_procedures(jcl_content)
        
        # Extract steps outside of procedures
        all_steps = extract_steps(jcl_content)
        
        # Filter out steps that are part of procedures
        proc_lines = self._get_procedure_line_ranges(jcl_content)
        jcl_data["steps"] = [step for step in all_steps if not self._is_in_procedure(step, proc_lines)]
        
        # Extract comments
        jcl_data["comments"] = extract_comments(jcl_content)
        
        # Extract bindplans with optional plan file parsing
        jcl_data["bindplans"] = extract_bindplans(jcl_content, base_path=base_path)
        
        return jcl_data
    
    def _get_procedure_line_ranges(self, jcl_content: str) -> List[tuple]:
        """Get line ranges for all procedures in the JCL content"""
        proc_ranges = []
        
        # Pattern for PROC and PEND statements
        proc_pattern = re.compile(r'//(?P<name>\S+)\s+PROC\s*(?P<params>.*?)(?:\n|$)', re.IGNORECASE)
        pend_pattern = re.compile(r'//\s+PEND\s*(?:\n|$)', re.IGNORECASE)
        
        lines = jcl_content.split('\n')
        proc_start = None
        
        for i, line in enumerate(lines):
            if proc_pattern.match(line.strip()):
                proc_start = i + 1  # 1-based line numbering
            elif pend_pattern.match(line.strip()) and proc_start is not None:
                proc_ranges.append((proc_start, i + 1))
                proc_start = None
        
        return proc_ranges
    
    def _is_in_procedure(self, step: Dict[str, Any], proc_line_ranges: List[tuple]) -> bool:
        """Check if a step is inside any procedure based on line number"""
        step_line = step.get('line', 0)
        for start, end in proc_line_ranges:
            if start <= step_line <= end:
                return True
        return False
    
    def to_json(self, jcl_data: Dict[str, Any], pretty: bool = False) -> str:
        """Convert parsed JCL data to JSON format"""
        if pretty:
            return json.dumps(jcl_data, indent=2)
        return json.dumps(jcl_data)


if __name__ == "__main__":
    import sys
    import os
    
    if len(sys.argv) < 2:
        print("Usage: python jcl_parser.py <jcl_file>")
        sys.exit(1)
    
    jcl_file = sys.argv[1]
    if not os.path.exists(jcl_file):
        print(f"Error: File '{jcl_file}' not found")
        sys.exit(1)
    
    parser = JCLParser()
    try:
        jcl_data = parser.parse_file(jcl_file)
        print(parser.to_json(jcl_data, pretty=True))
    except Exception as e:
        print(f"Error parsing JCL file: {e}")
        sys.exit(1)