"""
Extractor for BINDPLAN statements and linked plan files in JCL programs.
"""
import re
import os
from typing import List, Dict, Any, Optional

from jcl_parser.extractors.base_extractor import BaseExtractor


def extract_bindplans(source: str, base_path: Optional[str] = None, line_map: Optional[List[int]] = None) -> List[Dict[str, Any]]:
    """
    Extract BINDPLAN references from JCL source code.
    
    This function identifies EXEC statements that use BINDPLAN and extracts their PLAN parameters.
    It can optionally load and parse the associated plan files.
    
    Args:
        source: JCL source code.
        base_path: Optional base path to search for plan files. If provided, attempts to load plan files.
        line_map: Optional mapping of source line indices to original line numbers.
        
    Returns:
        List of dictionaries with information about each BINDPLAN statement.
    """
    # Pattern for EXEC statements with BINDPLAN
    bindplan_pattern = re.compile(
        r'//(?P<name>\S+)\s+EXEC\s+BINDPLAN\s*,?\s*PLAN=(?P<plan>\S+)(?:\s|,|//|$)',
        re.IGNORECASE
    )
    
    bindplans = []
    lines = source.split('\n')
    
    for line_num, line in enumerate(lines):
        # Skip empty lines and comments
        if not line.strip() or line.strip().startswith('//*'):
            continue
        
        # Check for BINDPLAN EXEC statement
        match = bindplan_pattern.search(line)
        if match:
            step_name = match.group('name')
            plan_name = match.group('plan')
            
            bindplan_info = {
                'name': step_name,
                'plan': plan_name,
                'line': line_num + 1,  # Convert to 1-based line numbers
                'include_files': [],
                'entry_point': None
            }
            
            # Try to load and parse the plan file if base_path is provided
            if base_path:
                plan_data = _load_and_parse_plan_file(plan_name, base_path)
                if plan_data:
                    bindplan_info['include_files'] = plan_data.get('include_files', [])
                    bindplan_info['entry_point'] = plan_data.get('entry_point')
                    bindplan_info['plan_file_path'] = plan_data.get('file_path')
                    bindplan_info['plan_file_found'] = True
                else:
                    bindplan_info['plan_file_found'] = False
            
            bindplans.append(bindplan_info)
    
    return bindplans


def _load_and_parse_plan_file(plan_name: str, base_path: str) -> Optional[Dict[str, Any]]:
    """
    Load and parse a plan file.
    
    Args:
        plan_name: Name of the plan (without extension, assumed to be .txt).
        base_path: Base path to search for the plan file.
        
    Returns:
        Dictionary with parsed plan data or None if file not found.
    """
    # Try to find the plan file with .txt extension
    plan_file = os.path.join(base_path, f"{plan_name}.txt")
    
    # Also try without extension in case it's already specified
    if not os.path.exists(plan_file):
        plan_file = os.path.join(base_path, plan_name)
    
    # Try in subdirectories like 'plans' or 'bind'
    if not os.path.exists(plan_file):
        for subdir in ['plans', 'bind', 'bindplans']:
            plan_file = os.path.join(base_path, subdir, f"{plan_name}.txt")
            if os.path.exists(plan_file):
                break
    
    if not os.path.exists(plan_file):
        return None
    
    try:
        return _parse_plan_file_content(plan_file)
    except Exception:
        return None


def _parse_plan_file_content(file_path: str) -> Dict[str, Any]:
    """
    Parse the content of a plan file.
    
    Extracts:
    - INCLUDE OBJLIB(...) statements
    - ENTRY statement
    - Other parameters
    
    Args:
        file_path: Path to the plan file.
        
    Returns:
        Dictionary with parsed plan data.
    """
    plan_data = {
        'include_files': [],
        'entry_point': None,
        'file_path': file_path,
        'parameters': {}
    }
    
    with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
        content = f.read()
    
    lines = content.split('\n')
    
    for line in lines:
        line_stripped = line.strip()
        
        # Skip empty lines and comments
        if not line_stripped or line_stripped.startswith('*'):
            continue
        
        # Extract INCLUDE OBJLIB statements
        include_match = re.match(r'INCLUDE\s+OBJLIB\s*\(\s*(\S+)\s*\)', line_stripped, re.IGNORECASE)
        if include_match:
            objlib_file = include_match.group(1)
            plan_data['include_files'].append(objlib_file)
        
        # Extract ENTRY statement
        entry_match = re.match(r'ENTRY\s+(\S+)', line_stripped, re.IGNORECASE)
        if entry_match:
            plan_data['entry_point'] = entry_match.group(1)
        
        # Extract other parameters (like AMODE, RMODE, etc.)
        if '=' in line_stripped and not line_stripped.startswith('#'):
            param_match = re.match(r'(\S+)\s*=\s*(.+)', line_stripped)
            if param_match:
                key, value = param_match.groups()
                plan_data['parameters'][key] = value.strip()
    
    return plan_data
