"""
Extractor for procedures (PROC/PEND blocks) in JCL programs.
"""
import re
from typing import List, Dict, Any, Optional

from jcl_parser.extractors.base_extractor import BaseExtractor
from jcl_parser.extractors.step_extractor import extract_steps


def extract_procedures(source: str, line_map: Optional[List[int]] = None) -> List[Dict[str, Any]]:
    """
    Extract procedures and their steps from JCL source code.
    
    Args:
        source: JCL source code.
        line_map: Optional mapping of source line indices to original line numbers.
        
    Returns:
        List of dictionaries with information about each procedure.
    """
    # Pattern for PROC and PEND statements
    proc_pattern = re.compile(
        r'//(?P<name>\S+)\s+PROC\s*(?P<params>.*?)(?:\n|$)',
        re.IGNORECASE
    )
    pend_pattern = re.compile(
        r'//\s+PEND\s*(?:\n|$)',
        re.IGNORECASE
    )
    
    procedures = []
    lines = source.split('\n')
    line_num = 0
    proc_start = None
    current_proc = None
    
    while line_num < len(lines):
        line = lines[line_num].strip()
        
        # Skip empty lines and comments
        if not line or line.startswith('//*'):
            line_num += 1
            continue
        
        # Check for PROC statement
        proc_match = proc_pattern.match(line)
        if proc_match:
            proc_start = line_num
            proc_name = proc_match.group('name')
            proc_params = proc_match.group('params')
            
            current_proc = {
                'name': proc_name,
                'parameters': BaseExtractor.parse_parameters(proc_params),
                'steps': [],
                'line': line_num + 1  # Convert to 1-based line numbers
            }
        
        # Check for PEND statement
        elif pend_pattern.match(line) and current_proc and proc_start is not None:
            # Extract all steps between PROC and PEND
            proc_source = '\n'.join(lines[proc_start+1:line_num])
            current_proc['steps'] = extract_steps(proc_source, line_map)
            
            procedures.append(current_proc)
            current_proc = None
            proc_start = None
        
        line_num += 1
    
    return procedures