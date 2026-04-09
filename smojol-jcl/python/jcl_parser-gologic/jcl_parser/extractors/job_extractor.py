"""
Extractor for JOB statements in JCL programs.
"""
import re
from typing import List, Dict, Any, Optional

from jcl_parser.extractors.base_extractor import BaseExtractor


def extract_jobs(source: str, line_map: Optional[List[int]] = None) -> List[Dict[str, Any]]:
    """
    Extract JOB statements from JCL source code.
    
    This function identifies JOB statements and parses their parameters.
    
    Args:
        source: JCL source code.
        line_map: Optional mapping of source line indices to original line numbers.
        
    Returns:
        List of dictionaries with information about each JOB statement.
    """
    # Pattern for JOB statements
    job_pattern = re.compile(
        r'//(?P<name>\S+)\s+JOB\s+(?P<params>.*?)(?:\n|$)',
        re.IGNORECASE
    )
    
    # Find all JOB statements
    results = BaseExtractor.find_matches(job_pattern, source, line_map)
    
    # Process results to add parsed parameters
    for result in results:
        job_name = result['groups']['name']
        job_params = result['groups']['params']
        
        # Parse parameters
        params = BaseExtractor.parse_parameters(job_params)
        
        # Add additional information to the result
        result['name'] = job_name  # Add name directly to the result
        result['parameters'] = params
    
    return results