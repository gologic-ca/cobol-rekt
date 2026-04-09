"""
Extractor for EXEC and DD statements in JCL programs.
"""
import re
from typing import List, Dict, Any, Optional

from jcl_parser.extractors.base_extractor import BaseExtractor


def extract_steps(source: str, line_map: Optional[List[int]] = None) -> List[Dict[str, Any]]:
    """
    Extract EXEC statements and their associated DD statements from JCL source code.
    
    Args:
        source: JCL source code.
        line_map: Optional mapping of source line indices to original line numbers.
        
    Returns:
        List of dictionaries with information about each step.
    """
    # Patterns for EXEC and DD statements
    exec_pattern = re.compile(
        r'//(?P<name>\S+)\s+EXEC\s+(?P<params>.*?)(?:\n|$)',
        re.IGNORECASE
    )
    dd_pattern = re.compile(
        r'//(?P<name>\S+)\s+DD\s+(?P<params>.*?)(?:\n|$)',
        re.IGNORECASE
    )
    
    # Find all EXEC statements first
    steps = []
    current_step = None
    
    # Split source into lines to process sequentially
    lines = source.split('\n')
    line_num = 0
    
    while line_num < len(lines):
        line = lines[line_num].strip()
        
        # Skip empty lines and comments
        if not line or line.startswith('//*'):
            line_num += 1
            continue
        
        # Check for EXEC statement
        exec_match = exec_pattern.match(line)
        if exec_match:
            # If we find a new EXEC, store the previous step
            if current_step:
                steps.append(current_step)
            
            # Create new step
            step_name = exec_match.group('name')
            step_params = exec_match.group('params')
            
            current_step = {
                'name': step_name,
                'parameters': BaseExtractor.parse_exec_parameters(step_params),
                'dd_statements': [],
                'line': line_num + 1  # Convert to 1-based line numbers
            }
            
        # Check for DD statement if we're in a step
        elif current_step:
            dd_match = dd_pattern.match(line)
            if dd_match:
                dd_name = dd_match.group('name')
                dd_params = dd_match.group('params')
                
                dd_statement = {
                    'name': dd_name,
                    'parameters': BaseExtractor.parse_parameters(dd_params),
                    'line': line_num + 1  # Convert to 1-based line numbers
                }
                
                current_step['dd_statements'].append(dd_statement)
        
        line_num += 1
    
    # Don't forget to add the last step
    if current_step:
        steps.append(current_step)
    
    return steps