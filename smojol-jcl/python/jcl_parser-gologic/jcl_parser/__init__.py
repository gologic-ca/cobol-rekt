"""
JCL Parser package for parsing and analyzing JCL (Job Control Language) files
"""

from .jcl_parser import JCLParser
from .interface import JCLInterface
from .logger import logger, LogLevel
from .extractors import (
    extract_jobs, 
    extract_steps, 
    extract_procedures,
    extract_comments, 
    BaseExtractor
)

__version__ = "0.1.0"
__package_name__ = "legacylens_jcl_parser"

__all__ = [
    'JCLParser', 
    'JCLInterface', 
    'logger', 
    'LogLevel', 
    'extract_jobs', 
    'extract_steps', 
    'extract_procedures', 
    'extract_comments', 
    'BaseExtractor'
]