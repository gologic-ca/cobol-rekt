#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Example extended JCL Parser
"""

import re
from typing import Dict, Any, List
from jcl_parser.jcl_parser import JCLParser


class ExtendedJCLParser(JCLParser):
    """Extended JCL Parser with conditional and include statement support"""
    
    def parse_string(self, jcl_content: str) -> Dict[str, Any]:
        """Parse JCL content from a string and return structured data with extensions"""
        # First get basic JCL structure from parent method
        jcl_data = super().parse_string(jcl_content)
        
        # Add extended parsing
        jcl_data["conditionals"] = self._extract_conditionals(jcl_content)
        jcl_data["includes"] = self._extract_includes(jcl_content)
        
        return jcl_data
    
    def _extract_conditionals(self, jcl_content: str) -> List[Dict[str, Any]]:
        """Extract conditional statements (IF/ELSE/ENDIF) from JCL content"""
        conditionals = []
        
        # Patterns for conditional statements
        if_pattern = re.compile(r'//\s+IF\s+(?P<condition>.+)(?:\n|$)', re.IGNORECASE)
        else_pattern = re.compile(r'//\s+ELSE\s*(?:\n|$)', re.IGNORECASE)
        endif_pattern = re.compile(r'//\s+ENDIF\s*(?:\n|$)', re.IGNORECASE)
        
        # Process line by line
        for line_num, line in enumerate(jcl_content.split('\n')):
            line = line.strip()
            
            # Check for IF statement
            if_match = if_pattern.match(line)
            if if_match:
                conditionals.append({
                    "type": "if",
                    "condition": if_match.group('condition'),
                    "line": line_num + 1
                })
                continue
            
            # Check for ELSE statement
            else_match = else_pattern.match(line)
            if else_match:
                conditionals.append({
                    "type": "else",
                    "line": line_num + 1
                })
                continue
            
            # Check for ENDIF statement
            endif_match = endif_pattern.match(line)
            if endif_match:
                conditionals.append({
                    "type": "endif",
                    "line": line_num + 1
                })
                continue
        
        return conditionals
    
    def _extract_includes(self, jcl_content: str) -> List[Dict[str, Any]]:
        """Extract INCLUDE statements from JCL content"""
        includes = []
        
        # Pattern for INCLUDE statements
        include_pattern = re.compile(
            r'//\s+INCLUDE\s+(?P<params>.+)(?:\n|$)', 
            re.IGNORECASE
        )
        
        # Process line by line
        for line_num, line in enumerate(jcl_content.split('\n')):
            line = line.strip()
            
            # Check for INCLUDE statement
            include_match = include_pattern.match(line)
            if include_match:
                params_text = include_match.group('params')
                
                # Parse parameters
                params = {}
                for param in params_text.split(','):
                    if '=' in param:
                        key, value = param.split('=', 1)
                        params[key.strip()] = value.strip()
                
                # Extract member name
                member = params.get('MEMBER', '')
                
                includes.append({
                    "type": "include",
                    "member": member,
                    "parameters": params,
                    "line": line_num + 1
                })
        
        return includes
