#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
BINDPLAN Extractor - Complete Usage Example

This example demonstrates how to use the BINDPLAN extractor with real-world
JCL files and plan files.
"""

import json
import tempfile
import os
from jcl_parser import JCLParser


def example_1_basic_bindplan_extraction():
    """
    Example 1: Basic BINDPLAN extraction from JCL
    """
    print("=" * 80)
    print("Example 1: Basic BINDPLAN Extraction")
    print("=" * 80)
    
    jcl_content = """//J59AIG   JOB (123456),'BATCH PROCESSING',CLASS=A,MSGCLASS=X
//STEP1    EXEC PGM=PROG1
//OUTFILE  DD DSNAME=OUTPUT.DATA,DISP=(NEW,KEEP)
//*
//         PEND
//*
//J59AIG   EXEC BINDPLAN,PLAN=J59AIG
//J59ELF   EXEC BINDPLAN,PLAN=J59ELF
//*
"""
    
    parser = JCLParser()
    jcl_data = parser.parse_string(jcl_content)
    
    print("\n✓ Extracted BINDPLAN references:")
    for bp in jcl_data['bindplans']:
        print(f"  - Step: {bp['name']}, Plan: {bp['plan']}, Line: {bp['line']}")
    
    print("\nFull BINDPLAN data:")
    print(json.dumps(jcl_data['bindplans'], indent=2))


def example_2_bindplan_with_plan_files():
    """
    Example 2: BINDPLAN extraction with associated plan file parsing
    """
    print("\n" + "=" * 80)
    print("Example 2: BINDPLAN with Plan File Parsing")
    print("=" * 80)
    
    jcl_content = """//J59AIG   JOB (123456),'BINDING JOB'
//BINDMAIN EXEC BINDPLAN,PLAN=J59AIG
"""
    
    # Create temporary directory with plan file
    with tempfile.TemporaryDirectory() as tmpdir:
        # Create plan file
        plan_file = os.path.join(tmpdir, 'J59AIG.txt')
        with open(plan_file, 'w') as f:
            f.write("""*
* LINK EDITOR PARAMETERS FOR J59AIG
*
  #PARMLIEN1 :=
  #PARMLIEN2 := AMODE=24,RMODE=24
*
* MAIN PROGRAM AND INCLUDED LIBRARIES
*
   INCLUDE OBJLIB(J59AIG00)
   INCLUDE OBJLIB(J59PTLF)
   INCLUDE OBJLIB(J59PRDF)
   INCLUDE OBJLIB(J59DM012)
   INCLUDE OBJLIB(J59EXCP)
   ENTRY J59AIG00
""")
        
        # Parse with plan file discovery
        parser = JCLParser()
        jcl_data = parser.parse_string(jcl_content, base_path=tmpdir)
        
        print("\n✓ BINDPLAN extracted with plan file information:")
        bp = jcl_data['bindplans'][0]
        print(f"  Step Name: {bp['name']}")
        print(f"  Plan Name: {bp['plan']}")
        print(f"  Entry Point: {bp['entry_point']}")
        print(f"  Plan File Found: {bp['plan_file_found']}")
        print(f"  Included Files: {bp['include_files']}")


def example_3_real_world_scenario():
    """
    Example 3: Real-world scenario with multiple BINDPLAN steps
    """
    print("\n" + "=" * 80)
    print("Example 3: Real-World Multi-Project Scenario")
    print("=" * 80)
    
    jcl_content = """//MULTIPROJ JOB (ACCT,SUB),'MULTI-PROJECT BIND',CLASS=A
//STEP0001  EXEC PGM=IEFBR14
//*
//PROJ1BND  EXEC BINDPLAN,PLAN=PROJ1MAIN
//PROJ2BND  EXEC BINDPLAN,PLAN=PROJ2MAIN
//PROJ3BND  EXEC BINDPLAN,PLAN=PROJ3MAIN
//LINKDBG   EXEC BINDPLAN,PLAN=DEBUGAPP
//
"""
    
    # Create temporary directory with plan files
    with tempfile.TemporaryDirectory() as tmpdir:
        plan_configs = {
            'PROJ1MAIN': """   INCLUDE OBJLIB(MAIN001)
   INCLUDE OBJLIB(MOD001)
   INCLUDE OBJLIB(MOD002)
   INCLUDE OBJLIB(UTIL001)
   ENTRY MAIN001
""",
            'PROJ2MAIN': """   INCLUDE OBJLIB(MAIN002)
   INCLUDE OBJLIB(CORE01)
   INCLUDE OBJLIB(CORE02)
   ENTRY MAIN002
""",
            'PROJ3MAIN': """   INCLUDE OBJLIB(MAIN003)
   INCLUDE OBJLIB(APP001)
   ENTRY MAIN003
""",
            'DEBUGAPP': """   INCLUDE OBJLIB(DEBUGMAIN)
   INCLUDE OBJLIB(DEBUGLIB)
   ENTRY DEBUGMAIN
"""
        }
        
        # Create plan files
        for plan_name, content in plan_configs.items():
            plan_file = os.path.join(tmpdir, f'{plan_name}.txt')
            with open(plan_file, 'w') as f:
                f.write(content)
        
        # Parse
        parser = JCLParser()
        jcl_data = parser.parse_string(jcl_content, base_path=tmpdir)
        
        print("\n✓ Multi-Project BINDPLAN Analysis:")
        print(f"Total BINDPLAN steps: {len(jcl_data['bindplans'])}")
        print()
        
        for bp in jcl_data['bindplans']:
            if bp['plan_file_found']:
                print(f"Project: {bp['name']:12} | "
                      f"Plan: {bp['plan']:12} | "
                      f"Entry: {bp['entry_point']:12} | "
                      f"Files: {len(bp['include_files'])}")
                print(f"  ├─ Included: {', '.join(bp['include_files'][:3])}", end="")
                if len(bp['include_files']) > 3:
                    print(f", +{len(bp['include_files']) - 3} more")
                else:
                    print()


def example_4_analysis_and_reporting():
    """
    Example 4: Analysis and reporting on BINDPLAN data
    """
    print("\n" + "=" * 80)
    print("Example 4: BINDPLAN Analysis and Reporting")
    print("=" * 80)
    
    jcl_content = """//ANALYSIS JOB (999),'ANALYSIS TEST'
//B1 EXEC BINDPLAN,PLAN=APP1
//B2 EXEC BINDPLAN,PLAN=APP2
//B3 EXEC BINDPLAN,PLAN=UTILS
"""
    
    with tempfile.TemporaryDirectory() as tmpdir:
        plans = {
            'APP1': """   INCLUDE OBJLIB(APP1MAIN)
   INCLUDE OBJLIB(APP1MOD1)
   INCLUDE OBJLIB(APP1MOD2)
   INCLUDE OBJLIB(COMMON)
   ENTRY APP1MAIN
""",
            'APP2': """   INCLUDE OBJLIB(APP2MAIN)
   INCLUDE OBJLIB(APP2MOD1)
   INCLUDE OBJLIB(COMMON)
   ENTRY APP2MAIN
""",
            'UTILS': """   INCLUDE OBJLIB(UTILMAIN)
   INCLUDE OBJLIB(STRINGS)
   INCLUDE OBJLIB(MATH)
   ENTRY UTILMAIN
"""
        }
        
        for name, content in plans.items():
            with open(os.path.join(tmpdir, f'{name}.txt'), 'w') as f:
                f.write(content)
        
        parser = JCLParser()
        jcl_data = parser.parse_string(jcl_content, base_path=tmpdir)
        
        # Analysis 1: Dependency analysis
        print("\n1. Shared Components Analysis:")
        all_modules = {}
        for bp in jcl_data['bindplans']:
            for module in bp['include_files']:
                if module not in all_modules:
                    all_modules[module] = []
                all_modules[module].append(bp['plan'])
        
        shared = {m: plans for m, plans in all_modules.items() if len(plans) > 1}
        print(f"   Shared modules: {list(shared.keys())}")
        for module, projects in shared.items():
            print(f"   └─ {module}: used by {', '.join(projects)}")
        
        # Analysis 2: Entry point summary
        print("\n2. Entry Points Summary:")
        for bp in jcl_data['bindplans']:
            if bp['plan_file_found']:
                print(f"   {bp['plan']:12} → {bp['entry_point']}")
        
        # Analysis 3: Module count by plan
        print("\n3. Module Count by Plan:")
        for bp in jcl_data['bindplans']:
            if bp['plan_file_found']:
                print(f"   {bp['plan']:12}: {len(bp['include_files']):2} modules")


if __name__ == '__main__':
    print("\n🚀 BINDPLAN Extractor - Complete Usage Examples\n")
    
    try:
        example_1_basic_bindplan_extraction()
        example_2_bindplan_with_plan_files()
        example_3_real_world_scenario()
        example_4_analysis_and_reporting()
        
        print("\n" + "=" * 80)
        print("✨ All examples completed successfully!")
        print("=" * 80 + "\n")
        
    except Exception as e:
        print(f"\n❌ Error running examples: {e}")
        import traceback
        traceback.print_exc()
