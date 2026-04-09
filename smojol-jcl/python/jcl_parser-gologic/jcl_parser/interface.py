#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
JCL Parser Interface - Provides a simple interface for interacting with the JCL Parser.
"""

import os
import json
from typing import Dict, List, Any, Optional, Union
from pathlib import Path
from .jcl_parser import JCLParser
from .logger import logger, LogLevel


class JCLInterface:
    """
    A simple interface for the JCL Parser.
    
    This class provides an easy-to-use interface for parsing JCL files and
    extracting information from them.
    
    Examples:
        >>> from src.interface import JCLInterface
        >>> jcl = JCLInterface()
        >>> # Parse a JCL file
        >>> result = jcl.parse_file("path/to/file.jcl")
        >>> # Get job name
        >>> job_name = jcl.get_job_name(result)
        >>> # Get all steps
        >>> steps = jcl.get_steps(result)
    """
    
    def __init__(self, log_level: str = "INFO"):
        """
        Initialize the JCL Interface.
        
        Args:
            log_level: The logging level (INFO, WARNING, DEBUG)
        """
        self.parser = JCLParser()
        self.set_log_level(log_level)
    
    def set_log_level(self, level: str) -> None:
        """
        Set the logging level.
        
        Args:
            level: The logging level (INFO, WARNING, DEBUG)
        """
        logger.set_level(LogLevel[level])
    
    def parse_file(self, file_path: str) -> Dict[str, Any]:
        """
        Parse a JCL file and return structured data.
        
        Args:
            file_path: Path to the JCL file
            
        Returns:
            Dictionary containing the parsed JCL data
            
        Raises:
            FileNotFoundError: If the file does not exist
        """
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"File not found: {file_path}")
        
        logger.info(f"Parsing JCL file: {file_path}")
        return self.parser.parse_file(file_path)
    
    def parse_string(self, jcl_content: str) -> Dict[str, Any]:
        """
        Parse JCL content from a string.
        
        Args:
            jcl_content: String containing JCL content
            
        Returns:
            Dictionary containing the parsed JCL data
        """
        logger.info("Parsing JCL from string")
        return self.parser.parse_string(jcl_content)
    
    def to_json(self, jcl_data: Dict[str, Any], pretty: bool = True) -> str:
        """
        Convert parsed JCL data to JSON.
        
        Args:
            jcl_data: Parsed JCL data
            pretty: Whether to format the JSON output (default: True)
            
        Returns:
            JSON string
        """
        return self.parser.to_json(jcl_data, pretty=pretty)
    
    def save_to_file(self, jcl_data: Dict[str, Any], output_path: str, pretty: bool = True) -> None:
        """
        Save parsed JCL data to a JSON file.
        
        Args:
            jcl_data: Parsed JCL data
            output_path: Path to the output file
            pretty: Whether to format the JSON output (default: True)
        """
        json_output = self.to_json(jcl_data, pretty=pretty)
        
        with open(output_path, 'w') as file:
            file.write(json_output)
        
        logger.info(f"JCL data saved to: {output_path}")
    
    # Helper methods for extracting information from parsed JCL data
    
    def get_job_name(self, jcl_data: Dict[str, Any]) -> str:
        """
        Get the job name from parsed JCL data.
        
        Args:
            jcl_data: Parsed JCL data
            
        Returns:
            Job name or empty string if not found
        """
        return jcl_data.get("job", {}).get("name", "")
    
    def get_job_parameters(self, jcl_data: Dict[str, Any]) -> Dict[str, str]:
        """
        Get job parameters from parsed JCL data.
        
        Args:
            jcl_data: Parsed JCL data
            
        Returns:
            Dictionary of job parameters or empty dict if not found
        """
        return jcl_data.get("job", {}).get("parameters", {})
    
    def get_steps(self, jcl_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        Get all steps from parsed JCL data.
        
        Args:
            jcl_data: Parsed JCL data
            
        Returns:
            List of steps or empty list if not found
        """
        return jcl_data.get("steps", [])
    
    def get_step_by_name(self, jcl_data: Dict[str, Any], step_name: str) -> Optional[Dict[str, Any]]:
        """
        Get a specific step by name from parsed JCL data.
        
        Args:
            jcl_data: Parsed JCL data
            step_name: Name of the step to find
            
        Returns:
            Step data or None if not found
        """
        steps = self.get_steps(jcl_data)
        for step in steps:
            if step.get("name") == step_name:
                return step
        return None
    
    def get_step_program(self, step_data: Dict[str, Any]) -> str:
        """
        Get the program name (PGM) from a step.
        
        Args:
            step_data: Step data from parsed JCL
            
        Returns:
            Program name or empty string if not found
        """
        params = step_data.get("parameters", {})
        return params.get("PGM", params.get("_value", ""))
    
    def get_dd_statements(self, step_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        Get all DD statements from a step.
        
        Args:
            step_data: Step data from parsed JCL
            
        Returns:
            List of DD statements or empty list if not found
        """
        return step_data.get("dd_statements", [])
    
    def get_dd_by_name(self, step_data: Dict[str, Any], dd_name: str) -> Optional[Dict[str, Any]]:
        """
        Get a specific DD statement by name from a step.
        
        Args:
            step_data: Step data from parsed JCL
            dd_name: Name of the DD statement to find
            
        Returns:
            DD statement data or None if not found
        """
        dd_statements = self.get_dd_statements(step_data)
        for dd in dd_statements:
            if dd.get("name") == dd_name:
                return dd
        return None
    
    def get_procedures(self, jcl_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        Get all procedures from parsed JCL data.
        
        Args:
            jcl_data: Parsed JCL data
            
        Returns:
            List of procedures or empty list if not found
        """
        return jcl_data.get("procedures", [])
    
    def get_comments(self, jcl_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        Get all comments from parsed JCL data.
        
        Args:
            jcl_data: Parsed JCL data
            
        Returns:
            List of comments or empty list if not found
        """
        return jcl_data.get("comments", [])