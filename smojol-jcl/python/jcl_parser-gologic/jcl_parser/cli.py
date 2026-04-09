#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Command-line interface for the JCL Parser
"""

import argparse
import sys
import os
from .jcl_parser import JCLParser
from .logger import logger, LogLevel


def main():
    """Main CLI entry point"""
    parser = argparse.ArgumentParser(
        description="Parse JCL files and output as JSON"
    )
    
    parser.add_argument(
        "jcl_file",
        help="Path to the JCL file to parse"
    )
    
    parser.add_argument(
        "-o", "--output",
        help="Output file path (if not specified, prints to stdout)",
        default=None
    )
    
    parser.add_argument(
        "-p", "--pretty",
        help="Pretty print the JSON output",
        action="store_true"
    )

    parser.add_argument(
        "-l", "--log-level",
        help="Set the logging level (INFO, WARNING, DEBUG)",
        choices=["INFO", "WARNING", "DEBUG"],
        default="INFO"
    )
    
    args = parser.parse_args()
    
    # Set the log level
    logger.set_level(LogLevel[args.log_level])
    logger.info(f"Starting JCL parser with log level: {args.log_level}")
    
    if not os.path.exists(args.jcl_file):
        logger.info(f"Error: File '{args.jcl_file}' not found")
        sys.exit(1)
    
    jcl_parser = JCLParser()
    
    try:
        logger.warning(f"Parsing file: {args.jcl_file}")
        jcl_data = jcl_parser.parse_file(args.jcl_file)
        logger.debug(f"Parsed JCL data: {jcl_data}")
        
        json_output = jcl_parser.to_json(jcl_data, pretty=args.pretty)
        
        if args.output:
            logger.warning(f"Writing output to: {args.output}")
            with open(args.output, 'w') as out_file:
                out_file.write(json_output)
            logger.info(f"Output written to {args.output}")
        else:
            print(json_output)
            
    except Exception as e:
        logger.info(f"Error parsing JCL file: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main() 