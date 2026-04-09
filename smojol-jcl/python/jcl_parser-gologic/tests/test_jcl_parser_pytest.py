#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Pytest-based tests for the JCL Parser with real-world examples
"""

import os
import json
import pytest
import tempfile
from jcl_parser.jcl_parser import JCLParser


@pytest.fixture
def parser():
    """Create and return a JCL parser instance"""
    return JCLParser()


@pytest.fixture
def simple_jcl_file():
    """Create a simple JCL test file"""
    temp_dir = tempfile.TemporaryDirectory()
    test_file = os.path.join(temp_dir.name, "simple.jcl")
    
    with open(test_file, "w") as f:
        f.write("""//TESTJOB JOB (ACCT#),'TEST JOB',CLASS=A,MSGCLASS=X,
//             MSGLEVEL=(1,1),NOTIFY=&SYSUID
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


@pytest.fixture
def complex_jcl_file():
    """Create a complex JCL test file with more real-world examples"""
    temp_dir = tempfile.TemporaryDirectory()
    test_file = os.path.join(temp_dir.name, "complex.jcl")
    
    with open(test_file, "w") as f:
        f.write("""//PRODRUN JOB (ACCT123,'DEPT'),'JOHN SMITH',CLASS=A,
//             MSGCLASS=X,MSGLEVEL=(1,1),NOTIFY=USER01,
//             REGION=0M,TIME=1440
//*
//* PRODUCTION JOB TO PROCESS DAILY TRANSACTIONS
//*=============================================
//*
//JOBLIB   DD DSN=SYS1.BATCH.LOADLIB,DISP=SHR
//         DD DSN=PROD.APPL.LOADLIB,DISP=SHR
//*
//CLEANUP EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  DELETE PROD.DAILY.REPORT PURGE
  IF LASTCC > 0 THEN SET MAXCC = 0
  DELETE PROD.ERROR.REPORT PURGE
  IF LASTCC > 0 THEN SET MAXCC = 0
/*
//*
//******************************************************************
//* STEP 1: EXTRACT DATA FROM DATABASE
//******************************************************************
//EXTRACT  EXEC PGM=DBEXTRAC,
//         PARM='DATE=&YYMMDD,REGION=EAST'
//SYSOUT   DD SYSOUT=*
//SYSUDUMP DD SYSOUT=*
//EXTRACT  DD DSN=PROD.DAILY.EXTRACT,
//            DISP=(NEW,CATLG,DELETE),
//            SPACE=(CYL,(100,50),RLSE),
//            DCB=(RECFM=FB,LRECL=800,BLKSIZE=0)
//DBPARM   DD *
  CONNECT=PRODUCTION
  ISOLATION=UR
  PLAN=DBEXTPLN
/*
//*
//******************************************************************
//* STEP 2: SORT THE EXTRACTED DATA
//******************************************************************
//SORT     EXEC PGM=SORT,COND=(0,LT,EXTRACT)
//SORTIN   DD DSN=PROD.DAILY.EXTRACT,
//            DISP=SHR
//SORTOUT  DD DSN=PROD.DAILY.SORTED,
//            DISP=(NEW,CATLG,DELETE),
//            SPACE=(CYL,(100,50),RLSE),
//            DCB=(RECFM=FB,LRECL=800,BLKSIZE=0)
//SYSOUT   DD SYSOUT=*
//SYSIN    DD *
  SORT FIELDS=(1,10,CH,A,11,8,PD,D)
  INCLUDE COND=(28,3,CH,EQ,C'ACT')
  OPTION DYNALLOC
/*
//*
//******************************************************************
//* STEP 3: PROCESS THE SORTED DATA
//******************************************************************
//PROCESS  EXEC PGM=DAILYPROC,COND=((0,LT,EXTRACT),(0,LT,SORT))
//INFILE   DD DSN=PROD.DAILY.SORTED,
//            DISP=SHR
//REPORT   DD DSN=PROD.DAILY.REPORT,
//            DISP=(NEW,CATLG,DELETE),
//            SPACE=(CYL,(10,5),RLSE),
//            DCB=(RECFM=FBA,LRECL=133,BLKSIZE=0)
//ERRFILE  DD DSN=PROD.ERROR.REPORT,
//            DISP=(NEW,CATLG,DELETE),
//            SPACE=(CYL,(5,2),RLSE),
//            DCB=(RECFM=FBA,LRECL=133,BLKSIZE=0)
//SYSOUT   DD SYSOUT=*
//SYSDUMP  DD SYSOUT=*
//SYSIN    DD *
  PROCESS_MODE=FULL
  ERROR_THRESHOLD=500
  DEBUG=NO
/*
//*
//******************************************************************
//* STEP 4: FTP THE REPORT TO PARTNER SYSTEM
//******************************************************************
//FTP      EXEC PGM=FTPBATCH,COND=((0,LT,EXTRACT),(0,LT,PROCESS))
//SYSPRINT DD SYSOUT=*
//OUTPUT   DD SYSOUT=*
//INPUT    DD *
  open partner.company.com
  user ftpuser password123
  binary
  cd /incoming/reports
  put 'PROD.DAILY.REPORT' DAILY_&YYMMDD..RPT
  quit
/*
//
""")
    
    yield test_file
    temp_dir.cleanup()


@pytest.fixture
def jcl_with_procs():
    """Create a JCL test file with nested procedures"""
    temp_dir = tempfile.TemporaryDirectory()
    test_file = os.path.join(temp_dir.name, "with_procs.jcl")
    
    with open(test_file, "w") as f:
        f.write("""//DBBACKUP JOB (DB123),'DATABASE BACKUP',CLASS=A,MSGCLASS=X,NOTIFY=DBA01
//*
//* JOB TO PERFORM DATABASE BACKUP
//*
//PROCLIB  JCLLIB ORDER=(SYS1.PROCLIB,DB.PROC.LIB)
//*
//BACKUP   PROC ENV=PROD,DATE=&YYMMDD
//*
//CLEANUP  EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  DELETE &ENV..BACKUP.&DATE PURGE
  IF LASTCC > 0 THEN SET MAXCC = 0
/*
//*
//BACKUPDB EXEC PGM=DBBACKUP,PARM='FULL,COMPRESS=YES'
//SYSPRINT DD SYSOUT=*
//BACKUP   DD DSN=&ENV..BACKUP.&DATE,
//            DISP=(NEW,CATLG,DELETE),
//            SPACE=(CYL,(1000,500),RLSE)
//DBPARM   DD *
  DATABASE=&ENV
  LOGRETAIN=YES
/*
//         PEND
//*
//STEPLIB  DD DSN=SYS1.DB2.LOADLIB,DISP=SHR
//         DD DSN=SYS1.BACKUP.LOADLIB,DISP=SHR
//*
//RUNPROD  EXEC BACKUP,ENV=PRODUCTION
//*
//RUNTEST  EXEC BACKUP,ENV=TEST
//*
""")
    
    yield test_file
    temp_dir.cleanup()


@pytest.fixture
def jcl_with_sysin():
    """Create a JCL file with SYSIN DD * data"""
    temp_dir = tempfile.TemporaryDirectory()
    test_file = os.path.join(temp_dir.name, "with_sysin.jcl")
    
    with open(test_file, "w") as f:
        f.write("""//REPORTING JOB (REP123),'MONTHLY REPORT',CLASS=A,MSGCLASS=X
//*
//* MONTHLY FINANCIAL REPORTING
//*
//STEP1    EXEC PGM=FINRPT
//SYSOUT   DD SYSOUT=*
//INPUT    DD DSN=FINANCE.MONTHLY.DATA,DISP=SHR
//OUTPUT   DD DSN=FINANCE.MONTHLY.REPORT,
//            DISP=(NEW,CATLG,DELETE),
//            SPACE=(CYL,(5,2)),
//            DCB=(RECFM=FBA,LRECL=133,BLKSIZE=0)
//SYSIN    DD *
REPORT TITLE='MONTHLY FINANCIAL ANALYSIS'
PERIOD=CURRENT_MONTH
SECTION ACCOUNTS_RECEIVABLE
  SUBTOTAL BY=REGION
  COMPARE WITH=PREVIOUS_MONTH
SECTION ACCOUNTS_PAYABLE
  SUBTOTAL BY=DEPARTMENT
  COMPARE WITH=PREVIOUS_MONTH
SECTION REVENUE
  SUBTOTAL BY=PRODUCT_LINE
  COMPARE WITH=(PREVIOUS_MONTH,PREVIOUS_YEAR)
END
/*
//
""")
    
    yield test_file
    temp_dir.cleanup()


@pytest.fixture
def jcl_with_if_else():
    """Create a JCL file with IF/THEN/ELSE logic"""
    temp_dir = tempfile.TemporaryDirectory()
    test_file = os.path.join(temp_dir.name, "with_if_else.jcl")
    
    with open(test_file, "w") as f:
        f.write("""//CONDTEST JOB (TEST),'CONDITIONAL JOB',CLASS=A,MSGCLASS=X
//*
//* JOB TO TEST CONDITIONAL PROCESSING
//*
//STEP1    EXEC PGM=IEFBR14
//*
// IF (RC = 0) THEN
//*
//STEP2A   EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  LISTCAT ENTRIES('TEST.FILE') ALL
/*
//*
// ELSE
//*
//STEP2B   EXEC PGM=IEFBR14
//DD1      DD DSN=TEST.FILE,
//            DISP=(NEW,CATLG,DELETE),
//            SPACE=(TRK,(1,1))
//*
// ENDIF
//*
//STEP3    EXEC PGM=IEFBR14
//*
""")
    
    yield test_file
    temp_dir.cleanup()


def test_simple_jcl_parsing(parser, simple_jcl_file):
    """Test parsing a simple JCL file"""
    jcl_data = parser.parse_file(simple_jcl_file)
    
    # Check job info
    assert jcl_data["job"]["name"] == "TESTJOB"
    assert "ACCT#" in jcl_data["job"]["parameters"]["_value"]
    
    # Check step info
    assert len(jcl_data["steps"]) == 1
    assert jcl_data["steps"][0]["name"] == "STEP1"
    assert jcl_data["steps"][0]["parameters"]["_value"] == "PGM=IEFBR14"
    
    # Check DD statements
    assert len(jcl_data["steps"][0]["dd_statements"]) == 1
    assert jcl_data["steps"][0]["dd_statements"][0]["name"] == "DD1"
    
    # Check comments
    assert len(jcl_data["comments"]) == 1
    assert jcl_data["comments"][0]["text"] == " This is a comment"
    
    # Check procedures
    assert len(jcl_data["procedures"]) == 1
    assert jcl_data["procedures"][0]["name"] == "PROC1"
    assert len(jcl_data["procedures"][0]["steps"]) == 1
    assert jcl_data["procedures"][0]["steps"][0]["name"] == "STEP2"


def test_complex_jcl_parsing(parser, complex_jcl_file):
    """Test parsing a complex JCL file with real-world examples"""
    jcl_data = parser.parse_file(complex_jcl_file)
    
    # Check job info
    assert jcl_data["job"]["name"] == "PRODRUN"
    assert "ACCT123" in jcl_data["job"]["parameters"]["_value"]
    
    # Verify all steps are present
    step_names = [step["name"] for step in jcl_data["steps"]]
    assert "CLEANUP" in step_names
    assert "EXTRACT" in step_names
    assert "SORT" in step_names
    assert "PROCESS" in step_names
    assert "FTP" in step_names
    
    # Check specific steps
    extract_step = next(step for step in jcl_data["steps"] if step["name"] == "EXTRACT")
    # For EXTRACT step, check that either PGM is set correctly or _value contains PGM=DBEXTRAC
    assert "PGM" in extract_step["parameters"] or "PGM=DBEXTRAC" in extract_step["parameters"]["_value"]
    
    # Check DD statements in SORT step
    sort_step = next(step for step in jcl_data["steps"] if step["name"] == "SORT")
    sort_dd_names = [dd["name"] for dd in sort_step["dd_statements"]]
    assert "SORTIN" in sort_dd_names
    assert "SORTOUT" in sort_dd_names
    assert "SYSOUT" in sort_dd_names
    assert "SYSIN" in sort_dd_names
    
    # Check comments
    assert len(jcl_data["comments"]) > 0
    comment_texts = [comment["text"] for comment in jcl_data["comments"]]
    assert any("PRODUCTION JOB" in text for text in comment_texts)


def test_jcl_with_procs(parser, jcl_with_procs):
    """Test parsing JCL with procedures"""
    jcl_data = parser.parse_file(jcl_with_procs)
    
    # Check job info
    assert jcl_data["job"]["name"] == "DBBACKUP"
    
    # Check procedures
    assert len(jcl_data["procedures"]) == 1
    proc = jcl_data["procedures"][0]
    assert proc["name"] == "BACKUP"
    
    # Check steps within the procedure
    proc_steps = proc["steps"]
    proc_step_names = [step["name"] for step in proc_steps]
    assert "CLEANUP" in proc_step_names
    assert "BACKUPDB" in proc_step_names
    
    # Check EXEC statements that use the procedure
    step_names = [step["name"] for step in jcl_data["steps"]]
    assert "RUNPROD" in step_names
    assert "RUNTEST" in step_names


def test_jcl_with_sysin(parser, jcl_with_sysin):
    """Test parsing JCL with SYSIN DD * data"""
    jcl_data = parser.parse_file(jcl_with_sysin)
    
    # Check job info
    assert jcl_data["job"]["name"] == "REPORTING"
    
    # Check STEP1
    step1 = jcl_data["steps"][0]
    assert step1["name"] == "STEP1"
    assert step1["parameters"]["_value"] == "PGM=FINRPT"
    
    # Check DD statements
    dd_names = [dd["name"] for dd in step1["dd_statements"]]
    assert "SYSOUT" in dd_names
    assert "INPUT" in dd_names
    assert "OUTPUT" in dd_names
    assert "SYSIN" in dd_names
    
    # Check SYSIN DD statement
    sysin_dd = next(dd for dd in step1["dd_statements"] if dd["name"] == "SYSIN")
    assert sysin_dd["parameters"]["_value"] == "*"


def test_to_json_format(parser, simple_jcl_file):
    """Test converting parsed data to JSON"""
    jcl_data = parser.parse_file(simple_jcl_file)
    
    # Test regular JSON output
    json_output = parser.to_json(jcl_data)
    parsed_json = json.loads(json_output)
    assert parsed_json["job"]["name"] == "TESTJOB"
    
    # Test pretty-printed JSON output
    pretty_json = parser.to_json(jcl_data, pretty=True)
    assert "  " in pretty_json  # Check for indentation
    parsed_pretty_json = json.loads(pretty_json)
    assert parsed_pretty_json["job"]["name"] == "TESTJOB"