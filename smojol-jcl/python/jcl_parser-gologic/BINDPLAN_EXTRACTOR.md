# BINDPLAN Extractor Documentation

## Overview

The BINDPLAN extractor is designed to parse and extract BINDPLAN references from JCL (Job Control Language) files. It identifies EXEC statements that use BINDPLAN with the PLAN parameter and optionally loads and parses the associated plan files.

## Features

- **Extract BINDPLAN references**: Identifies EXEC statements using BINDPLAN and extracts the PLAN parameter
- **Parse plan files**: Automatically loads and parses `.txt` plan files to extract:
  - `INCLUDE OBJLIB(...)` statements - lists of referenced object library files
  - `ENTRY` statement - identifies the main entry point
  - Parameters like AMODE and RMODE
- **Flexible file discovery**: Searches multiple locations for plan files:
  - Same directory as JCL file
  - `plans/`, `bind/`, or `bindplans/` subdirectories
- **Graceful error handling**: Continues parsing even if plan files are not found

## Usage

### Basic Usage

```python
from jcl_parser import JCLParser

# Create parser instance
parser = JCLParser()

# Parse JCL file (automatically searches for plan files in same directory)
jcl_data = parser.parse_file('/path/to/job.jcl')

# Access BINDPLAN data
bindplans = jcl_data['bindplans']
for bp in bindplans:
    print(f"Step: {bp['name']}")
    print(f"Plan: {bp['plan']}")
    print(f"Entry Point: {bp['entry_point']}")
    print(f"Included Files: {bp['include_files']}")
```

### Parse String with Optional Plan File Search

```python
# Parse JCL content from string with plan file search
jcl_content = """//JOB1 JOB (123456),'TEST'
//STEP1 EXEC BINDPLAN,PLAN=MYPROG
"""

# With plan file discovery
jcl_data = parser.parse_string(jcl_content, base_path='/path/to/plans')

# Without plan file discovery
jcl_data = parser.parse_string(jcl_content)
```

### Plan File Discovery Options

The extractor automatically searches for plan files using several strategies:

1. **Direct name match**: `PLAN=J59AIG` → looks for `J59AIG.txt`
2. **Name with extension**: If plan name already includes extension
3. **Subdirectories**: Searches in `plans/`, `bind/`, or `bindplans/` subdirectories

### Output Structure

#### BINDPLAN Entry Without Plan File

```json
{
  "name": "STEP1",
  "plan": "MYPROG",
  "line": 5,
  "include_files": [],
  "entry_point": null
}
```

#### BINDPLAN Entry With Plan File Found

```json
{
  "name": "STEP1",
  "plan": "MYPROG",
  "line": 5,
  "include_files": [
    "MAINPROG",
    "SUBPROG1",
    "SUBPROG2"
  ],
  "entry_point": "MAINPROG",
  "plan_file_path": "/path/to/MYPROG.txt",
  "plan_file_found": true
}
```

## Plan File Format

Plan files are text files with a format like:

```
*
* PARAMETERS FOR LINK EDITOR
*
  #PARMLIEN1 :=
  #PARMLIEN2 := AMODE=24,RMODE=24
*
* LOAD MODULE SPECIFICATIONS
*
   INCLUDE OBJLIB(MAINPROG)
   INCLUDE OBJLIB(SUBLIB1)
   INCLUDE OBJLIB(SUBLIB2)
   ENTRY MAINPROG
```

### Supported Directives

- **Comments**: Lines starting with `*` are treated as comments
- **INCLUDE OBJLIB(name)**: Specifies object library components
- **ENTRY name**: Specifies the main entry point
- **Parameters**: Key=value pairs are parsed and stored

## Integration with JCL Parser

The BINDPLAN extractor is automatically integrated into the main JCLParser. When you parse a JCL file, the output includes:

```python
jcl_data = {
    "job": {...},           # Job statement
    "steps": [...],         # EXEC steps
    "procedures": [...],    # PROC/PEND blocks
    "comments": [...],      # Comments
    "bindplans": [...]      # ← BINDPLAN references and data
}
```

## Examples

### Complete Example

```python
from jcl_parser import JCLParser
import json

jcl_content = """//J59AIG   JOB (123456),'TEST JOB'
//STEP1    EXEC BINDPLAN,PLAN=J59AIG
//STEP2    EXEC BINDPLAN,PLAN=J59ELF
"""

parser = JCLParser()
result = parser.parse_string(jcl_content, base_path='/opt/plans')

# Print BINDPLAN section
print(json.dumps(result['bindplans'], indent=2))
```

### Filtering Bindplans

```python
# Get all BINDPLAN references that have plan files
found_plans = [bp for bp in jcl_data['bindplans'] if bp.get('plan_file_found', False)]

# Get entry points
entry_points = [bp['entry_point'] for bp in found_plans if bp['entry_point']]

# Get all included files
all_includes = []
for bp in found_plans:
    all_includes.extend(bp['include_files'])
```

## API Reference

### extract_bindplans(source, base_path=None, line_map=None)

Extracts BINDPLAN references from JCL source code.

**Parameters:**
- `source` (str): JCL source code
- `base_path` (Optional[str]): Base path to search for plan files. If None, plan files are not loaded.
- `line_map` (Optional[List[int]]): Optional mapping of source line indices to original line numbers

**Returns:**
- List[Dict[str, Any]]: List of BINDPLAN dictionaries

### JCLParser.parse_file(file_path, search_plan_files=True)

Parses a JCL file.

**Parameters:**
- `file_path` (str): Path to the JCL file
- `search_plan_files` (bool): If True, automatically searches for plan files in the same directory

**Returns:**
- Dict[str, Any]: Parsed JCL data including BINDPLAN information

### JCLParser.parse_string(jcl_content, base_path=None)

Parses JCL content from a string.

**Parameters:**
- `jcl_content` (str): JCL content as string
- `base_path` (Optional[str]): Base path to search for plan files

**Returns:**
- Dict[str, Any]: Parsed JCL data including BINDPLAN information

## Error Handling

The extractor handles errors gracefully:

- **Missing plan files**: If a plan file is not found, the entry will not have `plan_file_found=True` and arrays will be empty
- **Unreadable files**: If a plan file cannot be read, it's silently skipped
- **Invalid format**: Lines that don't match known patterns are ignored

## Notes

- Plan file searching is automatic when using `parse_file()` - it searches relative to the JCL file location
- Plan file searching requires explicit `base_path` parameter when using `parse_string()`
- BINDPLAN steps are extracted as separate entries from regular EXEC steps in the `steps` array
- The extractor is case-insensitive for JCL keywords (EXEC, BINDPLAN, PLAN, etc.)
