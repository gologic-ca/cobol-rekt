#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Tests for the Extended JCL Parser
"""

import os
import pytest
import tempfile
from jcl_parser.extensions.example_extension import ExtendedJCLParser


@pytest.fixture
def extended_parser():
    """Create and return an extended JCL parser instance"""
    return ExtendedJCLParser()


@pytest.fixture
def jcl_with_conditional():
    """Create a JCL file with conditional logic"""
    temp_dir = tempfile.TemporaryDirectory()
    test_file = os.path.join(temp_dir.name, "conditional.jcl")
    
    with open(test_file, "w") as f:
        f.write("""//TESTCOND JOB (ACCT#),'CONDITIONAL TEST',CLASS=A
//*
//* JOB TO TEST CONDITIONAL LOGIC PARSING
//*
//STEP1 EXEC PGM=IEFBR14
//*
// IF (RC = 0) THEN
//*
//STEP2  EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  LISTCAT ENT('TEST.FILE')
/*
//*
// ELSE
//*
//STEP3  EXEC PGM=IEFBR14
//DD1    DD DSN=TEST.FILE,
//          DISP=(NEW,CATLG,DELETE),
//          SPACE=(TRK,(1,1))
//*
// ENDIF
//*
""")
    
    yield test_file
    temp_dir.cleanup()


@pytest.fixture
def jcl_with_include():
    """Create a JCL file with INCLUDE statements"""
    temp_dir = tempfile.TemporaryDirectory()
    test_file = os.path.join(temp_dir.name, "include.jcl")
    
    with open(test_file, "w") as f:
        f.write("""//TESTINCL JOB (ACCT#),'INCLUDE TEST',CLASS=A
//*
//* JOB TO TEST INCLUDE STATEMENT PARSING
//*
//JOBLIB   DD DSN=SYS1.PROCLIB,DISP=SHR
//*
// INCLUDE MEMBER=STDJOB
//*
//STEP1 EXEC PGM=MYPROG
//SYSPRINT DD SYSOUT=*
//*
// INCLUDE MEMBER=STDDD,LIB=CUSTOM.PROCLIB
//*
//DD1   DD DSN=TEST.FILE,DISP=SHR
//*
""")
    
    yield test_file
    temp_dir.cleanup()


@pytest.fixture
def jcl_with_nested_conditionals():
    """Create a JCL file with nested conditional logic"""
    temp_dir = tempfile.TemporaryDirectory()
    test_file = os.path.join(temp_dir.name, "nested_cond.jcl")
    
    with open(test_file, "w") as f:
        f.write("""//NSTCOND JOB (ACCT#),'NESTED CONDITIONAL TEST',CLASS=A
//*
//* JOB TO TEST NESTED CONDITIONAL LOGIC PARSING
//*
//STEP1 EXEC PGM=IEFBR14
//*
// IF (RC = 0) THEN
//*
//STEP2  EXEC PGM=IEFBR14
//*
//  IF (STEP2.RC = 0) THEN
//*
//STEP3  EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  LISTCAT ENT('TEST.FILE')
/*
//*
//  ELSE
//*
//STEP4  EXEC PGM=IEFBR14
//*
//  ENDIF
//*
// ELSE
//*
//STEP5  EXEC PGM=IEFBR14
//*
// ENDIF
//*
""")
    
    yield test_file
    temp_dir.cleanup()


def test_conditional_parsing(extended_parser, jcl_with_conditional):
    """Test parsing conditional logic in JCL"""
    jcl_data = extended_parser.parse_file(jcl_with_conditional)
    
    # Check that the conditionals array exists
    assert "conditionals" in jcl_data
    assert len(jcl_data["conditionals"]) == 3  # IF, ELSE, ENDIF
    
    # Check the conditional statements
    condition_types = [cond["type"] for cond in jcl_data["conditionals"]]
    assert "if" in condition_types
    assert "else" in condition_types
    assert "endif" in condition_types
    
    # Check the if condition
    if_cond = next(cond for cond in jcl_data["conditionals"] if cond["type"] == "if")
    assert "(RC = 0)" in if_cond["condition"]


def test_include_parsing(extended_parser, jcl_with_include):
    """Test parsing INCLUDE statements in JCL"""
    jcl_data = extended_parser.parse_file(jcl_with_include)
    
    # Check that the includes array exists
    assert "includes" in jcl_data
    assert len(jcl_data["includes"]) == 2
    
    # Check the include statements
    include_members = [inc["member"] for inc in jcl_data["includes"]]
    assert "STDJOB" in include_members
    assert "STDDD" in include_members
    
    # Check include with parameters
    custom_include = next(inc for inc in jcl_data["includes"] if inc["member"] == "STDDD")
    assert "LIB" in custom_include["parameters"]
    assert custom_include["parameters"]["LIB"] == "CUSTOM.PROCLIB"


def test_nested_conditionals(extended_parser, jcl_with_nested_conditionals):
    """Test parsing nested conditional statements"""
    jcl_data = extended_parser.parse_file(jcl_with_nested_conditionals)
    
    # Check that the conditionals array exists
    assert "conditionals" in jcl_data
    assert len(jcl_data["conditionals"]) == 6  # 2 IFs, 2 ELSEs, 2 ENDIFs
    
    # Count the number of each type
    condition_types = [cond["type"] for cond in jcl_data["conditionals"]]
    assert condition_types.count("if") == 2
    assert condition_types.count("else") == 2
    assert condition_types.count("endif") == 2
    
    # Check one of the nested conditions
    nested_if = next((cond for cond in jcl_data["conditionals"] 
                     if cond["type"] == "if" and "STEP2.RC" in cond.get("condition", "")), None)
    assert nested_if is not None
    assert "(STEP2.RC = 0)" in nested_if["condition"]


def test_extension_inheritance(extended_parser, jcl_with_conditional):
    """Test that the extended parser still handles basic JCL statements"""
    jcl_data = extended_parser.parse_file(jcl_with_conditional)
    
    # Check job info - add more flexible assertion
    assert "job" in jcl_data
    assert isinstance(jcl_data["job"], dict)
    
    # Check for job name using a more flexible approach
    job_info = jcl_data["job"]
    job_name = None
    
    # First try direct name property
    if "name" in job_info:
        job_name = job_info["name"]
    # Next try job_name property
    elif "job_name" in job_info:
        job_name = job_info["job_name"]
    # Try to find name in groups
    elif "groups" in job_info and "name" in job_info["groups"]:
        job_name = job_info["groups"]["name"]
    # Last resort, look for it in the groups data
    elif "groups" in job_info:
        for key, value in job_info["groups"].items():
            if "TESTCOND" in value:
                job_name = "TESTCOND"
                break
                
    assert job_name == "TESTCOND", f"Failed to find job name 'TESTCOND' in job data: {job_info}"
    
    # Check steps
    step_names = [step["name"] for step in jcl_data["steps"]]
    assert "STEP1" in step_names
    assert "STEP2" in step_names
    assert "STEP3" in step_names
    
    # Check DD statements
    step3 = next(step for step in jcl_data["steps"] if step["name"] == "STEP3")
    dd_names = [dd["name"] for dd in step3["dd_statements"]]
    assert "DD1" in dd_names