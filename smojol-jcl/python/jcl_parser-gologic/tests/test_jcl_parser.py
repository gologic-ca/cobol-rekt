#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Tests for the JCL Parser
"""

import unittest
import os
import json
import tempfile
from jcl_parser.jcl_parser import JCLParser


class TestJCLParser(unittest.TestCase):
    """Test cases for the JCL Parser"""

    def setUp(self):
        """Set up test environment"""
        self.parser = JCLParser()
        
        # Create a temporary JCL file for testing
        self.temp_dir = tempfile.TemporaryDirectory()
        self.test_jcl_path = os.path.join(self.temp_dir.name, "test.jcl")
        
        with open(self.test_jcl_path, "w") as f:
            f.write("""//TESTJOB JOB (ACCT),'TEST JOB',CLASS=A,MSGCLASS=X
//STEP1 EXEC PGM=IEFBR14
//* This is a comment
//DD1 DD DSN=TEST.DSN,DISP=SHR
//PROC1 PROC 
//STEP2 EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
// PEND
""")
    
    def tearDown(self):
        """Clean up test environment"""
        self.temp_dir.cleanup()
    
    def test_parse_file(self):
        """Test parsing a JCL file"""
        jcl_data = self.parser.parse_file(self.test_jcl_path)
        
        # Check job info
        self.assertEqual(jcl_data["job"]["name"], "TESTJOB")
        self.assertIn("ACCT", jcl_data["job"]["parameters"]["_value"])
        
        # Check step info
        self.assertEqual(len(jcl_data["steps"]), 1)
        self.assertEqual(jcl_data["steps"][0]["name"], "STEP1")
        self.assertEqual(jcl_data["steps"][0]["parameters"]["_value"], "PGM=IEFBR14")
        
        # Check DD statements
        self.assertEqual(len(jcl_data["steps"][0]["dd_statements"]), 1)
        self.assertEqual(jcl_data["steps"][0]["dd_statements"][0]["name"], "DD1")
        
        # Check comments
        self.assertEqual(len(jcl_data["comments"]), 1)
        self.assertEqual(jcl_data["comments"][0]["text"], " This is a comment")
        
        # Check procedures
        self.assertEqual(len(jcl_data["procedures"]), 1)
        self.assertEqual(jcl_data["procedures"][0]["name"], "PROC1")
        self.assertEqual(len(jcl_data["procedures"][0]["steps"]), 1)
        self.assertEqual(jcl_data["procedures"][0]["steps"][0]["name"], "STEP2")
    
    def test_to_json(self):
        """Test converting parsed data to JSON"""
        jcl_data = self.parser.parse_file(self.test_jcl_path)
        json_output = self.parser.to_json(jcl_data)
        
        # Verify that output is valid JSON
        parsed_back = json.loads(json_output)
        self.assertEqual(parsed_back["job"]["name"], "TESTJOB")


if __name__ == "__main__":
    unittest.main()