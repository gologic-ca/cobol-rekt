"""
Extractor for comments in JCL programs.
"""
import re
from typing import List, Dict, Any, Optional

from jcl_parser.extractors.base_extractor import BaseExtractor


def extract_comments(source: str, line_map: Optional[List[int]] = None) -> List[Dict[str, Any]]:
    """
    Extract comments from JCL source code.
    
    Args:
        source: JCL source code.
        line_map: Optional mapping of source line indices to original line numbers.
        
    Returns:
        List of dictionaries with information about each comment.
    """
    # Pattern for comments
    comment_pattern = re.compile(r'//\*(.*)(?:\n|$)')
    
    # Find all comments
    results = []
    
    for line_num, line in enumerate(source.split('\n')):
        match = comment_pattern.match(line.strip())
        if match:
            comment = {
                'text': match.group(1),
                'line': line_num + 1  # Convert to 1-based line numbers
            }
            results.append(comment)
    
    return results