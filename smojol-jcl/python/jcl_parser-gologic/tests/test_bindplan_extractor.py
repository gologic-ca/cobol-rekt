"""
Test for BINDPLAN extractor
"""
import os
import tempfile
import json
from jcl_parser.jcl_parser import JCLParser


def test_bindplan_extraction():
    """Test BINDPLAN extraction from JCL"""
    
    # Sample JCL with BINDPLAN
    jcl_content = """//J59AIG   JOB (123456),'TEST JOB',CLASS=A
//STEP1    EXEC PGM=PROG1
//*
//         PEND
//**********************************************************************
//*
//J59AIG   EXEC BINDPLAN,PLAN=J59AIG
//J59ELF   EXEC BINDPLAN,PLAN=J59ELF
//J59ELN   EXEC BINDPLAN,PLAN=J59ELN
//*
"""
    
    # Create temporary directory with plan files
    with tempfile.TemporaryDirectory() as tmpdir:
        # Create plan files
        plan_files = {
            'J59AIG.txt': """*
* PARAMETRES DE L'EDITEUR DE LIEN
*
  #PARMLIEN1 :=
  #PARMLIEN2 := AMODE=24,RMODE=24
*
* CARACTERISTIQUES OPERATOIRES DU LOAD MODULE
*
   INCLUDE OBJLIB(J59AIG00)
   INCLUDE OBJLIB(J59PTLF)
   INCLUDE OBJLIB(J59PRDF)
   ENTRY J59AIG00
""",
            'J59ELF.txt': """*
* Plan file for J59ELF
*
   INCLUDE OBJLIB(J59ELF00)
   INCLUDE OBJLIB(J59ELFX1)
   ENTRY J59ELF00
""",
            'J59ELN.txt': """*
* Plan file for J59ELN
*
   INCLUDE OBJLIB(J59ELN00)
   INCLUDE OBJLIB(J59ELNX1)
   INCLUDE OBJLIB(J59ELNX2)
   ENTRY J59ELN00
"""
        }
        
        # Write plan files
        for plan_name, plan_content in plan_files.items():
            with open(os.path.join(tmpdir, plan_name), 'w') as f:
                f.write(plan_content)
        
        # Parse JCL with plan files
        parser = JCLParser()
        jcl_data = parser.parse_string(jcl_content, base_path=tmpdir)
        
        print("=== BINDPLAN Extraction Test ===\n")
        print(json.dumps(jcl_data, indent=2))
        
        # Assertions
        assert len(jcl_data['bindplans']) == 3, f"Expected 3 bindplans, got {len(jcl_data['bindplans'])}"
        
        # Check first bindplan
        bindplan1 = jcl_data['bindplans'][0]
        assert bindplan1['name'] == 'J59AIG', f"Expected name 'J59AIG', got {bindplan1['name']}"
        assert bindplan1['plan'] == 'J59AIG', f"Expected plan 'J59AIG', got {bindplan1['plan']}"
        assert bindplan1['plan_file_found'] == True
        assert bindplan1['entry_point'] == 'J59AIG00'
        assert 'J59AIG00' in bindplan1['include_files']
        assert 'J59PTLF' in bindplan1['include_files']
        
        print("\n✓ All assertions passed!")


def test_bindplan_without_plan_files():
    """Test BINDPLAN extraction without actual plan files"""
    
    jcl_content = """//J59AIG   JOB (123456),'TEST JOB',CLASS=A
//J59AIG   EXEC BINDPLAN,PLAN=J59AIG
//J59ELF   EXEC BINDPLAN,PLAN=J59ELF
"""
    
    parser = JCLParser()
    # This should not fail, just not find the plan files
    jcl_data = parser.parse_string(jcl_content)
    
    print("\n=== BINDPLAN Extraction Without Plan Files Test ===\n")
    print(json.dumps(jcl_data, indent=2))
    
    assert len(jcl_data['bindplans']) == 2
    # plan_file_found is only set when base_path is provided
    assert 'plan_file_found' not in jcl_data['bindplans'][0] or jcl_data['bindplans'][0]['plan_file_found'] == False
    
    print("\n✓ Test passed!")


def test_bindplan_with_file_parsing():
    """Test parsing JCL file with BINDPLAN references"""
    
    with tempfile.TemporaryDirectory() as tmpdir:
        # Create JCL file
        jcl_file = os.path.join(tmpdir, 'test.jcl')
        with open(jcl_file, 'w') as f:
            f.write("""//J59AIG   JOB (123456),'TEST JOB',CLASS=A
//STEP1    EXEC BINDPLAN,PLAN=J59AIG
""")
        
        # Create plan file
        plan_file = os.path.join(tmpdir, 'J59AIG.txt')
        with open(plan_file, 'w') as f:
            f.write("""   INCLUDE OBJLIB(MAINPROG)
   INCLUDE OBJLIB(SUBPROG1)
   ENTRY MAINPROG
""")
        
        # Parse file
        parser = JCLParser()
        jcl_data = parser.parse_file(jcl_file)
        
        print("\n=== File Parsing Test ===\n")
        print(json.dumps(jcl_data, indent=2))
        
        assert len(jcl_data['bindplans']) == 1
        assert jcl_data['bindplans'][0]['entry_point'] == 'MAINPROG'
        
        print("\n✓ File parsing test passed!")


if __name__ == '__main__':
    test_bindplan_extraction()
    test_bindplan_without_plan_files()
    test_bindplan_with_file_parsing()
    print("\n✨ All BINDPLAN extraction tests passed!")
