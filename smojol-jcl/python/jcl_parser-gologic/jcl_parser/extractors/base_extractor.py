"""
Base extractor for JCL elements with common functionality.
"""
import re
from typing import List, Dict, Any, Pattern, Match, Optional


class BaseExtractor:
    """Base class for JCL element extractors with common utility methods."""
    
    @staticmethod
    def find_matches(pattern: Pattern, source: str, line_map: Optional[List[int]] = None) -> List[Dict[str, Any]]:
        """
        Find all matches of a regex pattern in source and return structured results.
        
        Args:
            pattern: Regular expression pattern to match.
            source: JCL source code.
            line_map: Optional mapping of source line indices to original line numbers.
            
        Returns:
            List of dictionaries with match information.
        """
        results = []
        
        for match in pattern.finditer(source):
            result = BaseExtractor.create_match_result(match, line_map, source)
            results.append(result)
            
        return results
    
    @staticmethod
    def create_match_result(match: Match, line_map: Optional[List[int]], source: str) -> Dict[str, Any]:
        """
        Create a structured result dictionary from a regex match.
        
        Args:
            match: Regex match object.
            line_map: Optional mapping of source line indices to original line numbers.
            source: JCL source code.
            
        Returns:
            Dictionary with match information.
        """
        # Extract matched text and position
        match_text = match.group(0)
        start_pos = match.start()
        end_pos = match.end()
        
        # Determine line number
        line_number = BaseExtractor.get_line_number(start_pos, source, line_map)
        
        # Create base result dictionary
        result = {
            'match': match_text,
            'start': start_pos,
            'end': end_pos,
            'line': line_number,
            'groups': {}
        }
        
        # Add named groups to the result
        if match.groupdict():
            for name, value in match.groupdict().items():
                if value is not None:
                    result['groups'][name] = value
        
        # Add numbered groups to the result if there are no named groups
        if not match.groupdict():
            for i, group in enumerate(match.groups(), 1):
                if group is not None:
                    result['groups'][f'group{i}'] = group
        
        return result
    
    @staticmethod
    def get_line_number(position: int, source: str, line_map: Optional[List[int]] = None) -> int:
        """
        Determine the line number for a position in the source.
        
        Args:
            position: Character position in source.
            source: JCL source code.
            line_map: Optional mapping of source line indices to original line numbers.
            
        Returns:
            Line number (1-based).
        """
        if line_map:
            # Find the line number using the line map
            line_count = 0
            for i, char in enumerate(source):
                if i >= position:
                    break
                if char == '\n':
                    line_count += 1
            
            # Check if the line is in the map
            if line_count < len(line_map):
                return line_map[line_count]
            else:
                return line_count + 1
        else:
            # Calculate line number directly from source
            return source[:position].count('\n') + 1
    
    @staticmethod
    def parse_parameters(param_string: str) -> Dict[str, str]:
        """Parse JCL parameters into a dictionary"""
        params = {}
        # Handle quoted strings and comma-separated parameters
        current_key = None
        current_value = ""
        in_quotes = False
        i = 0
        
        # If param_string is empty, return with _value empty
        if not param_string.strip():
            params["_value"] = ""
            return params
        
        while i < len(param_string):
            if param_string[i] == "'" and (i == 0 or param_string[i-1] != '\\'):
                in_quotes = not in_quotes
                current_value += param_string[i]
            elif param_string[i] == ',' and not in_quotes:
                if current_key:
                    params[current_key] = current_value.strip()
                
                # Find next key
                key_value = param_string[i+1:].strip().split('=', 1)
                if len(key_value) == 2:
                    current_key = key_value[0].strip()
                    current_value = key_value[1].strip()
                    i += len(key_value[0]) + len(key_value[1]) + 1  # +1 for the equals sign
                else:
                    current_key = None
                    current_value = key_value[0].strip()
                    i += len(key_value[0])
            else:
                current_value += param_string[i]
            i += 1
        
        if current_key and current_value:
            params[current_key] = current_value.strip()
        
        # Ensure _value is set if we have parameters but no key
        if current_value and not current_key:
            params["_value"] = current_value.strip()
        
        # If there are no parameters at all or we didn't set _value, set it to the original string
        if not params or "_value" not in params:
            params["_value"] = param_string.strip()
            
        return params
    
    @staticmethod
    def parse_exec_parameters(param_string: str) -> Dict[str, str]:
        """Special parsing for EXEC parameters to handle PARM values correctly"""
        params = BaseExtractor.parse_parameters(param_string)
        
        # Always ensure the full parameter string is in _value
        params["_value"] = param_string.strip()
        
        # Handle basic PGM=name case
        pgm_match = re.search(r'PGM=(\S+)', param_string)
        if pgm_match:
            params["PGM"] = pgm_match.group(1).rstrip(',')
        
        # Handle PARM values in quotes or not in quotes
        parm_match = re.search(r'PARM=\'([^\']+)\'|PARM=(\S+)', param_string)
        if parm_match:
            params["PARM"] = parm_match.group(1) if parm_match.group(1) else parm_match.group(2)
        
        # Add PARM= to _value if it's in the original string but not added yet
        if "PARM=" in param_string and "PARM=" not in params["_value"]:
            params["_value"] = param_string.strip()
            
        return params