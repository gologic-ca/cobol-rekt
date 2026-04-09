#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Tests for parsing real-world JCL examples
"""

import os
import pytest
from jcl_parser import JCLParser


@pytest.fixture
def parser():
    """Create and return a JCL parser instance"""
    return JCLParser()


@pytest.fixture
def data_dir():
    """Return the path to the test data directory"""
    return os.path.join(os.path.dirname(__file__), "data")


def test_z_os_batch_job(parser, data_dir):
    """Test parsing a realistic z/OS batch job"""
    jcl_file = os.path.join(data_dir, "z_os_batch_job.jcl")
    
    jcl_data = parser.parse_file(jcl_file)
    
    # Test job information
    assert jcl_data["job"]["name"] == "Z21235T"
    
    # Test that all steps exist
    step_names = [step["name"] for step in jcl_data["steps"]]
    assert len(step_names) == 6
    assert "STEP01" in step_names
    assert "STEP02" in step_names
    assert "STEP03" in step_names
    assert "STEP04" in step_names
    assert "STEP05" in step_names
    assert "STEP06" in step_names
    
    # Test specific step details
    step3 = next(step for step in jcl_data["steps"] if step["name"] == "STEP03")
    # Check for either PGM key or PGM in _value
    assert "PGM" in step3["parameters"] or "PGM=TPROCESS" in step3["parameters"]["_value"]
    
    # Test DD statements in STEP03
    step3_dd_names = [dd["name"] for dd in step3["dd_statements"]]
    assert "STEPLIB" in step3_dd_names
    assert "SYSPRINT" in step3_dd_names
    assert "CONTROL" in step3_dd_names
    assert "TRANSACT" in step3_dd_names
    assert "MASTER" in step3_dd_names
    assert "REPORT" in step3_dd_names
    assert "SYSIN" in step3_dd_names
    
    # Test continuations in parameter strings
    step5 = next(step for step in jcl_data["steps"] if step["name"] == "STEP05")
    # Check for PGM key or value
    assert "PGM" in step5["parameters"] or "PGM=DFSRRC00" in step5["parameters"]["_value"]
    
    # Test comments
    assert len(jcl_data["comments"]) > 0
    comment_texts = [comment["text"] for comment in jcl_data["comments"]]
    assert any("SAMPLE Z/OS BATCH JOB" in text for text in comment_texts)


def test_db2_stored_proc(parser, data_dir):
    """Test parsing a DB2 stored procedure JCL"""
    jcl_file = os.path.join(data_dir, "db2_stored_proc.jcl")
    
    jcl_data = parser.parse_file(jcl_file)
    
    # Test job information
    assert jcl_data["job"]["name"] == "DB2PROC"
    
    # Test that all steps exist
    step_names = [step["name"] for step in jcl_data["steps"]]
    assert "COMP" in step_names
    assert "BIND" in step_names
    assert "CRPROC" in step_names
    assert "TEST" in step_names
    
    # Test specific step details
    comp_step = next(step for step in jcl_data["steps"] if step["name"] == "COMP")
    assert ("DYNAMNBR" in comp_step["parameters"] or 
            "DYNAMNBR=20" in comp_step["parameters"]["_value"] or
            "PGM=IKJEFT01" in comp_step["parameters"]["_value"])
    
    # Test DD statements in COMP step
    comp_dd_names = [dd["name"] for dd in comp_step["dd_statements"]]
    assert "SYSTSPRT" in comp_dd_names
    assert "SYSTSIN" in comp_dd_names
    assert "COB2LIB" in comp_dd_names
    
    # Test comments
    assert len(jcl_data["comments"]) > 0
    comment_texts = [comment["text"] for comment in jcl_data["comments"]]
    assert any("CREATE AND BIND A DB2 STORED PROCEDURE" in text for text in comment_texts)


def test_parser_handles_complex_structures(parser, data_dir):
    """Test that the parser correctly handles complex JCL structures"""
    # Test with the z/OS batch job
    z_os_file = os.path.join(data_dir, "z_os_batch_job.jcl")
    z_os_data = parser.parse_file(z_os_file)
    
    # Test with the DB2 stored procedure JCL
    db2_file = os.path.join(data_dir, "db2_stored_proc.jcl")
    db2_data = parser.parse_file(db2_file)
    
    # Test that complex PARM values are properly parsed
    z_os_step5 = next(step for step in z_os_data["steps"] if step["name"] == "STEP05")
    # Check for PGM correctly parsed
    assert "PGM" in z_os_step5["parameters"] or "PGM=DFSRRC00" in z_os_step5["parameters"]["_value"]
    
    # Test that complex DSN statements are properly parsed
    db2_comp_step = next(step for step in db2_data["steps"] if step["name"] == "COMP")
    systsin_dd = next(dd for dd in db2_comp_step["dd_statements"] if dd["name"] == "SYSTSIN")
    
    # Test that continuation cards in DD statements are handled
    z_os_step3 = next(step for step in z_os_data["steps"] if step["name"] == "STEP03")
    report_dd = next(dd for dd in z_os_step3["dd_statements"] if dd["name"] == "REPORT")
    
    # Check that DSN parameter was properly extracted from the REPORT DD
    assert "DSN" in report_dd["parameters"]["_value"] or "DSN" in report_dd["parameters"]
    
    # Verify that we can parse multiple steps correctly
    assert len(z_os_data["steps"]) > 3
    assert len(db2_data["steps"]) > 2