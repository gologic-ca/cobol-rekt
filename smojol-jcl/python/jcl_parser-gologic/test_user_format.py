#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Test BINDPLAN Extractor with Real-World Format Examples

This test uses the exact JCL and plan file formats provided in the requirements.
"""

import tempfile
import os
import json
from jcl_parser import JCLParser


def test_user_provided_format():
    """
    Test with the exact JCL and plan file formats from user requirements.
    """
    print("=" * 80)
    print("Testing with User-Provided Format Examples")
    print("=" * 80)
    
    # Exact JCL format from user
    jcl_content = """//J59AIG   JOB (ACCT,SUB),'PRODUCTION JOB',CLASS=A
//STEP0001 EXEC PGM=COBOL1
//INPUT    DD DSNAME=INFILE,DISP=SHR
//OUTPUT   DD DSNAME=OUTFILE,DISP=(NEW,KEEP)
//*
//         PEND
//**********************************************************************
//*
//J59AIG   EXEC BINDPLAN,PLAN=J59AIG
//J59ELF   EXEC BINDPLAN,PLAN=J59ELF
//J59ELN   EXEC BINDPLAN,PLAN=J59ELN
//*
%%ENDIF
//
"""
    
    # Exact plan file formats from user (reformatted)
    plan_j59aig = """*
* PARAMETRES DE L'EDITEUR DE LIEN
*
  #PARMLIEN1 :=
  #PARMLIEN2 := AMODE=24,RMODE=24
*
* CARACTERISTIQUES OPERATOIRES DU LOAD MODULE
*
* #LIBRAIRIE   :=
* #CICS        :=
* #LOT_NON_IMS :=
* #DB2         :=
* #TSO         :=
* #IMS         :=
* #TRFPROD     :=
*
   INCLUDE OBJLIB(J59AIG00)
   INCLUDE OBJLIB(J59PTLF)
   INCLUDE OBJLIB(J59PRDF)
   INCLUDE OBJLIB(J59DM012)
   INCLUDE OBJLIB(J59DM100)
   INCLUDE OBJLIB(J59DM113)
   INCLUDE OBJLIB(J59DM114)
   INCLUDE OBJLIB(J59DTLC)
   INCLUDE OBJLIB(J59EXCP)
   INCLUDE OBJLIB(J59LMA00)
   INCLUDE OBJLIB(J99TDCLK)
   INCLUDE OBJLIB(VSAMATRB)
   INCLUDE OBJLIB(W30ACTAB)
   INCLUDE OBJLIB(Z99EXDB2)
   INCLUDE OBJLIB(Z99CAF)
   INCLUDE OBJLIB(Z99TINS)
   INCLUDE OBJLIB(Z99CONMD)
   INCLUDE OBJLIB(J59CLCDT)
   INCLUDE OBJLIB(Z99CBRAP)
   INCLUDE OBJLIB(Z99VALDT)
   ENTRY J59AIG00
"""

    plan_j59elf = """*
* PLAN FILE FOR J59ELF
*
  #PARMLIEN := AMODE=24,RMODE=24
*
   INCLUDE OBJLIB(J59ELF00)
   INCLUDE OBJLIB(J59ELFA)
   INCLUDE OBJLIB(J59ELFB)
   INCLUDE OBJLIB(COMMON)
   ENTRY J59ELF00
"""

    plan_j59eln = """*
* PLAN FILE FOR J59ELN
*
   INCLUDE OBJLIB(J59ELN00)
   INCLUDE OBJLIB(J59ELNA)
   INCLUDE OBJLIB(J59ELNB)
   INCLUDE OBJLIB(COMMON)
   ENTRY J59ELN00
"""
    
    # Create temporary directory with plan files
    with tempfile.TemporaryDirectory() as tmpdir:
        # Write plan files
        plan_files = {
            'J59AIG.txt': plan_j59aig,
            'J59ELF.txt': plan_j59elf,
            'J59ELN.txt': plan_j59eln
        }
        
        for filename, content in plan_files.items():
            filepath = os.path.join(tmpdir, filename)
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)
        
        # Parse the JCL
        parser = JCLParser()
        jcl_data = parser.parse_string(jcl_content, base_path=tmpdir)
        
        print("\n✓ JCL Parsing Complete\n")
        print("Summary:")
        print(f"  Job name: {jcl_data['job'].get('name', 'N/A')}")
        print(f"  BINDPLAN steps found: {len(jcl_data['bindplans'])}")
        print()
        
        # Display BINDPLAN details
        print("BINDPLAN Details:")
        print("-" * 80)
        
        for i, bp in enumerate(jcl_data['bindplans'], 1):
            print(f"\n{i}. BINDPLAN Reference:")
            print(f"   Step Name:      {bp['name']}")
            print(f"   Plan Name:      {bp['plan']}")
            print(f"   Line Number:    {bp['line']}")
            print(f"   Entry Point:    {bp['entry_point']}")
            print(f"   Files Found:    {len(bp['include_files'])}")
            print(f"   Plan File:      {'Found' if bp['plan_file_found'] else 'Not Found'}")
            
            if bp['include_files']:
                print(f"   Included OBJLIB:")
                for j, obj in enumerate(bp['include_files'], 1):
                    print(f"     {j:2}. {obj}")
        
        print("\n" + "-" * 80)
        print("\nFull JSON Output:")
        print(json.dumps(jcl_data['bindplans'], indent=2))
        
        # Assertions
        assert len(jcl_data['bindplans']) == 3, "Expected 3 BINDPLAN entries"
        
        # Check J59AIG
        bp1 = jcl_data['bindplans'][0]
        assert bp1['name'] == 'J59AIG'
        assert bp1['entry_point'] == 'J59AIG00'
        assert 'J59AIG00' in bp1['include_files']
        assert len(bp1['include_files']) == 20  # Count of includes in plan
        
        # Check J59ELF
        bp2 = jcl_data['bindplans'][1]
        assert bp2['name'] == 'J59ELF'
        assert bp2['entry_point'] == 'J59ELF00'
        assert 'J59ELF00' in bp2['include_files']
        assert 'COMMON' in bp2['include_files']
        
        # Check J59ELN
        bp3 = jcl_data['bindplans'][2]
        assert bp3['name'] == 'J59ELN'
        assert bp3['entry_point'] == 'J59ELN00'
        assert 'COMMON' in bp3['include_files']
        
        print("\n✅ All assertions passed!")
        print("\n" + "=" * 80)
        print("Test Result: SUCCESS ✨")
        print("=" * 80)


def analyze_dependencies():
    """
    Analyze dependencies between plans
    """
    print("\n" + "=" * 80)
    print("Dependency Analysis")
    print("=" * 80)
    
    # Sample data
    bindplans = [
        {
            'name': 'J59AIG',
            'plan': 'J59AIG',
            'include_files': ['J59AIG00', 'J59PTLF', 'COMMON', 'UTILS'],
            'entry_point': 'J59AIG00'
        },
        {
            'name': 'J59ELF',
            'plan': 'J59ELF',
            'include_files': ['J59ELF00', 'COMMON', 'UTILS'],
            'entry_point': 'J59ELF00'
        },
        {
            'name': 'J59ELN',
            'plan': 'J59ELN',
            'include_files': ['J59ELN00', 'COMMON', 'UTILS'],
            'entry_point': 'J59ELN00'
        }
    ]
    
    # Build dependency map
    module_to_plans = {}
    for bp in bindplans:
        for module in bp['include_files']:
            if module not in module_to_plans:
                module_to_plans[module] = []
            module_to_plans[module].append(bp['plan'])
    
    # Find shared modules
    shared_modules = {m: p for m, p in module_to_plans.items() if len(p) > 1}
    
    print("\nShared Modules (used by multiple plans):")
    for module, plans in sorted(shared_modules.items()):
        print(f"  • {module:20} → {', '.join(plans)}")
    
    print(f"\nTotal Plans: {len(bindplans)}")
    print(f"Total Unique Modules: {len(module_to_plans)}")
    print(f"Shared Modules: {len(shared_modules)}")
    
    # Module count per plan
    print("\nModule Count by Plan:")
    for bp in bindplans:
        print(f"  {bp['plan']:12} → {len(bp['include_files']):2} modules (Entry: {bp['entry_point']})")


if __name__ == '__main__':
    try:
        test_user_provided_format()
        analyze_dependencies()
        print("\n\n🎉 Real-world format test completed successfully!")
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
