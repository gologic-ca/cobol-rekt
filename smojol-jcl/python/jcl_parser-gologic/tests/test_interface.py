#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Tests for the JCL Interface
"""

import os
import json
import pytest
import tempfile
from jcl_parser.interface import JCLInterface
from jcl_parser.logger import LogLevel

@pytest.fixture
def interface():
    """Create and return a JCL interface instance"""
    return JCLInterface(log_level="INFO")

@pytest.fixture
def simple_jcl_content():
    """Return simple JCL content as a string"""
    return """//TESTJOB JOB (ACCT#),'TEST JOB',CLASS=A,MSGCLASS=X
//STEP1 EXEC PGM=IEFBR14
//* This is a comment
//DD1 DD DSN=TEST.DSN,DISP=SHR
//PROC1 PROC 
//STEP2 EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
// PEND
"""

@pytest.fixture
def jcl_file():
    """Create a temporary JCL file for testing"""
    temp_dir = tempfile.TemporaryDirectory()
    test_file = os.path.join(temp_dir.name, "test.jcl")
    
    with open(test_file, "w") as f:
        f.write("""//TESTJOB JOB (ACCT#),'TEST JOB',CLASS=A,MSGCLASS=X
//STEP1 EXEC PGM=IEFBR14
//* This is a comment
//DD1 DD DSN=TEST.DSN,DISP=SHR
//PROC1 PROC 
//STEP2 EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
// PEND
""")
    
    yield test_file
    temp_dir.cleanup()

def test_interface_initialization():
    """Test interface initialization with different log levels"""
    # Test default initialization
    interface = JCLInterface()
    assert interface.parser is not None
    
    # Test initialization with different log levels
    interface_debug = JCLInterface(log_level="DEBUG")
    interface_warning = JCLInterface(log_level="WARNING")
    
    assert interface_debug is not None
    assert interface_warning is not None

def test_set_log_level(interface):
    """Test setting the log level"""
    # Change log level
    interface.set_log_level("DEBUG")
    # Change to another level
    interface.set_log_level("WARNING")
    # Back to default
    interface.set_log_level("INFO")

def test_parse_file(interface, jcl_file):
    """Test parsing a JCL file"""
    jcl_data = interface.parse_file(jcl_file)
    
    # Verify basic structure
    assert "job" in jcl_data
    assert "steps" in jcl_data
    assert "procedures" in jcl_data
    assert "comments" in jcl_data
    
    # Check job data
    assert jcl_data["job"]["name"] == "TESTJOB"
    assert "ACCT#" in jcl_data["job"]["parameters"]["_value"]

def test_parse_file_not_found(interface):
    """Test handling of non-existent file"""
    with pytest.raises(FileNotFoundError):
        interface.parse_file("nonexistent_file.jcl")

def test_parse_string(interface, simple_jcl_content):
    """Test parsing JCL from a string"""
    jcl_data = interface.parse_string(simple_jcl_content)
    
    # Verify basic structure
    assert "job" in jcl_data
    assert "steps" in jcl_data
    assert "procedures" in jcl_data
    assert "comments" in jcl_data
    
    # Check job data
    assert jcl_data["job"]["name"] == "TESTJOB"
    
    # Check step data
    assert len(jcl_data["steps"]) == 1
    assert jcl_data["steps"][0]["name"] == "STEP1"

def test_to_json(interface, simple_jcl_content):
    """Test converting parsed data to JSON"""
    jcl_data = interface.parse_string(simple_jcl_content)
    
    # Test regular JSON output
    json_output = interface.to_json(jcl_data, pretty=False)
    parsed_json = json.loads(json_output)  # Ensure it's valid JSON
    assert parsed_json["job"]["name"] == "TESTJOB"
    
    # Test pretty-printed JSON output
    pretty_json = interface.to_json(jcl_data, pretty=True)
    assert "  " in pretty_json  # Check for indentation
    parsed_pretty_json = json.loads(pretty_json)
    assert parsed_pretty_json["job"]["name"] == "TESTJOB"

def test_save_to_file(interface, simple_jcl_content):
    """Test saving parsed data to a file"""
    jcl_data = interface.parse_string(simple_jcl_content)
    
    # Save to a temporary file
    temp_dir = tempfile.TemporaryDirectory()
    output_path = os.path.join(temp_dir.name, "output.json")
    
    try:
        interface.save_to_file(jcl_data, output_path)
        
        # Verify the file exists and contains valid JSON
        assert os.path.exists(output_path)
        
        with open(output_path, 'r') as f:
            content = f.read()
            parsed_json = json.loads(content)
            assert parsed_json["job"]["name"] == "TESTJOB"
    finally:
        temp_dir.cleanup()

def test_get_job_name(interface, simple_jcl_content):
    """Test getting job name from parsed data"""
    jcl_data = interface.parse_string(simple_jcl_content)
    job_name = interface.get_job_name(jcl_data)
    assert job_name == "TESTJOB"

def test_get_job_parameters(interface, simple_jcl_content):
    """Test getting job parameters from parsed data"""
    jcl_data = interface.parse_string(simple_jcl_content)
    job_params = interface.get_job_parameters(jcl_data)
    assert "_value" in job_params
    assert "ACCT#" in job_params["_value"]

def test_get_steps(interface, simple_jcl_content):
    """Test getting steps from parsed data"""
    jcl_data = interface.parse_string(simple_jcl_content)
    steps = interface.get_steps(jcl_data)
    assert len(steps) == 1
    assert steps[0]["name"] == "STEP1"

def test_get_step_by_name(interface, simple_jcl_content):
    """Test getting a specific step by name"""
    jcl_data = interface.parse_string(simple_jcl_content)
    
    # Get existing step
    step = interface.get_step_by_name(jcl_data, "STEP1")
    assert step is not None
    assert step["name"] == "STEP1"
    
    # Try to get non-existent step
    non_existent_step = interface.get_step_by_name(jcl_data, "NONEXISTENT")
    assert non_existent_step is None

def test_get_step_program(interface, simple_jcl_content):
    """Test getting program name from a step"""
    jcl_data = interface.parse_string(simple_jcl_content)
    step = interface.get_step_by_name(jcl_data, "STEP1")
    
    program = interface.get_step_program(step)
    assert "IEFBR14" in program

def test_get_dd_statements(interface, simple_jcl_content):
    """Test getting DD statements from a step"""
    jcl_data = interface.parse_string(simple_jcl_content)
    step = interface.get_step_by_name(jcl_data, "STEP1")
    
    dd_statements = interface.get_dd_statements(step)
    assert len(dd_statements) == 1
    assert dd_statements[0]["name"] == "DD1"

def test_get_dd_by_name(interface, simple_jcl_content):
    """Test getting a specific DD statement by name"""
    jcl_data = interface.parse_string(simple_jcl_content)
    step = interface.get_step_by_name(jcl_data, "STEP1")
    
    # Get existing DD
    dd = interface.get_dd_by_name(step, "DD1")
    assert dd is not None
    assert dd["name"] == "DD1"
    assert "DSN=TEST.DSN" in dd["parameters"]["_value"]
    
    # Try to get non-existent DD
    non_existent_dd = interface.get_dd_by_name(step, "NONEXISTENT")
    assert non_existent_dd is None

def test_get_procedures(interface, simple_jcl_content):
    """Test getting procedures from parsed data"""
    jcl_data = interface.parse_string(simple_jcl_content)
    procedures = interface.get_procedures(jcl_data)
    
    assert len(procedures) == 1
    assert procedures[0]["name"] == "PROC1"
    assert len(procedures[0]["steps"]) == 1
    assert procedures[0]["steps"][0]["name"] == "STEP2"

def test_get_comments(interface, simple_jcl_content):
    """Test getting comments from parsed data"""
    jcl_data = interface.parse_string(simple_jcl_content)
    comments = interface.get_comments(jcl_data)
    
    assert len(comments) == 1
    assert comments[0]["text"] == " This is a comment"

def test_empty_data_handling(interface):
    """Test handling of empty or invalid data"""
    # Create empty data structure
    empty_data = {}
    
    # Test all getter methods with empty data
    assert interface.get_job_name(empty_data) == ""
    assert interface.get_job_parameters(empty_data) == {}
    assert interface.get_steps(empty_data) == []
    assert interface.get_step_by_name(empty_data, "STEP") is None
    assert interface.get_procedures(empty_data) == []
    assert interface.get_comments(empty_data) == []
    
    # Test with a step that has no parameters
    step_with_no_params = {"name": "STEP1"}
    assert interface.get_step_program(step_with_no_params) == ""
    assert interface.get_dd_statements(step_with_no_params) == []
    assert interface.get_dd_by_name(step_with_no_params, "DD") is None

# Add main section to make it easier to run individual tests directly
if __name__ == "__main__":
    print("=== Running JCL Interface Tests ===")
    
    # Setup
    interface = JCLInterface(log_level="INFO")
    jcl_content = """//TESTJOB JOB (ACCT#),'TEST JOB',CLASS=A,MSGCLASS=X
//STEP1 EXEC PGM=IEFBR14
//* This is a comment
//DD1 DD DSN=TEST.DSN,DISP=SHR
//PROC1 PROC 
//STEP2 EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
// PEND
"""
    
    # Parse the JCL content
    print("Testing parse_string...")
    jcl_data = interface.parse_string(jcl_content)
    print(f"Job name: {interface.get_job_name(jcl_data)}")
    print(f"Number of steps: {len(interface.get_steps(jcl_data))}")
    print(f"Number of procedures: {len(interface.get_procedures(jcl_data))}")
    print(f"Number of comments: {len(interface.get_comments(jcl_data))}")
    
    # Test the JSON conversion
    print("\nTesting to_json...")
    pretty_json = interface.to_json(jcl_data, pretty=True)
    print(f"JSON output (first 100 chars): {pretty_json[:100]}...")
    
    # Test some helper methods
    print("\nTesting helper methods...")
    steps = interface.get_steps(jcl_data)
    step = steps[0] if steps else None
    if step:
        print(f"Step name: {step.get('name')}")
        print(f"Program: {interface.get_step_program(step)}")
        
        dd_statements = interface.get_dd_statements(step)
        print(f"Number of DD statements: {len(dd_statements)}")
        if dd_statements:
            dd = dd_statements[0]
            print(f"DD name: {dd.get('name')}")
    
    print("\n=== All tests completed ===")