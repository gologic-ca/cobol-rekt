# BINDPLAN Extractor Implementation - Summary

## Overview

Successfully implemented a new **BINDPLAN Extractor** for the JCL Parser that enables parsing and extraction of BIND PLAN references in JCL files. This extractor automatically discovers and parses associated plan files to extract detailed information about linked components.

## What Was Implemented

### 1. **Core Extractor Module** (`bindplan_extractor.py`)

A complete extractor that:
- **Extracts BINDPLAN references** from JCL files using regex patterns
- **Parses associated plan files** automatically (`.txt` format)
- **Extracts INCLUDE OBJLIB statements** from plan files
- **Identifies ENTRY points** in plan files
- **Handles flexible file discovery** with multiple search strategies
- **Provides graceful error handling** for missing files

### 2. **Integration with JCL Parser** 

Modified `jcl_parser.py` to:
- Import the new `extract_bindplans` function
- Add `bindplans` to the output structure
- Support optional `base_path` parameter for plan file discovery
- Automatically search for plan files when parsing JCL files

### 3. **Documentation**

- **BINDPLAN_EXTRACTOR.md**: Complete API reference and usage documentation
- **bindplan_examples.py**: Four comprehensive working examples demonstrating different usage scenarios
- Inline code documentation with docstrings

### 4. **Comprehensive Tests**

- **test_bindplan_extractor.py**: Three test cases covering:
  - BINDPLAN extraction with plan files
  - BINDPLAN extraction without plan files
  - File parsing with plan file discovery

## File Structure

```
jcl_parser/
  extractors/
    bindplan_extractor.py        ← New file
    __init__.py                  ← Updated
  jcl_parser.py                  ← Updated
    
BINDPLAN_EXTRACTOR.md           ← New documentation
bindplan_examples.py            ← New examples
tests/
  test_bindplan_extractor.py     ← New tests
```

## Key Features

### 1. **Automatic Plan File Discovery**

The extractor automatically searches for plan files in:
- Same directory as JCL file (when using `parse_file`)
- Subdirectories: `plans/`, `bind/`, `bindplans/`
- Multiple file naming patterns: `.txt` extension, or as-is

### 2. **Plan File Parsing**

Extracts from plan files:
- **INCLUDE OBJLIB(name)** statements
- **ENTRY name** declarations
- **Parameters** (AMODE, RMODE, etc.)
- **Comments** (lines starting with `*`)

### 3. **Output Structure**

Each BINDPLAN entry includes:
```json
{
  "name": "STEP_NAME",
  "plan": "PLAN_NAME",
  "line": 7,
  "include_files": ["MOD1", "MOD2", "MOD3"],
  "entry_point": "MAIN",
  "plan_file_path": "/path/to/plan.txt",
  "plan_file_found": true
}
```

## Usage Examples

### Simple Usage - Parse JCL with Auto Plan Discovery

```python
from jcl_parser import JCLParser

parser = JCLParser()
jcl_data = parser.parse_file('/path/to/job.jcl')

for bp in jcl_data['bindplans']:
    print(f"Plan: {bp['plan']}, Entry: {bp['entry_point']}")
```

### With Custom Plan Path

```python
parser = JCLParser()
jcl_data = parser.parse_string(jcl_content, base_path='/custom/plans/path')
```

### Analysis Example

```python
# Find shared components
all_modules = {}
for bp in jcl_data['bindplans']:
    for module in bp['include_files']:
        all_modules.setdefault(module, []).append(bp['plan'])

shared = {m: p for m, p in all_modules.items() if len(p) > 1}
```

## Integration Points

### Modified Files

**jcl_parser.py**:
- Added `import os` for path handling
- Added `from jcl_parser.extractors.bindplan_extractor import extract_bindplans`
- Updated `parse_file()` to support `search_plan_files` parameter
- Updated `parse_string()` to support `base_path` parameter
- Added `bindplans` to output structure
- Added call to `extract_bindplans()` in `parse_string()`

**extractors/__init__.py**:
- Added import of `extract_bindplans`
- Added to `__all__` export list

### New Files

**extractors/bindplan_extractor.py** (200+ lines):
- `extract_bindplans()` - main function
- `_load_and_parse_plan_file()` - file discovery
- `_parse_plan_file_content()` - plan file parsing

## Test Results

All tests pass successfully:
✅ BINDPLAN extraction with plan files
✅ BINDPLAN extraction without plan files  
✅ File parsing integration

## Example Output

```json
"bindplans": [
  {
    "name": "PROJ1BND",
    "plan": "PROJ1MAIN",
    "line": 4,
    "include_files": [
      "MAIN001",
      "MOD001",
      "MOD002",
      "COMMON"
    ],
    "entry_point": "MAIN001",
    "plan_file_path": "/opt/plans/PROJ1MAIN.txt",
    "plan_file_found": true
  }
]
```

## How to Use

1. **Install/Update Package**:
   ```bash
   pip install -e .
   ```

2. **Basic Usage**:
   ```python
   from jcl_parser import JCLParser
   
   parser = JCLParser()
   result = parser.parse_file('myjob.jcl')
   print(result['bindplans'])
   ```

3. **Advanced Usage**: See `bindplan_examples.py` and `BINDPLAN_EXTRACTOR.md`

## Technical Details

### Supported BINDPLAN Syntax

The extractor recognizes:
```
//STEPNAME EXEC BINDPLAN,PLAN=planname
//STEPNAME EXEC BINDPLAN, PLAN=planname
//STEPNAME EXEC  BINDPLAN  ,  PLAN = planname
```

### Plan File Location Discovery

1. Direct path: `/path/to/PLANNAME.txt`
2. With subdirectory: `/path/to/plans/PLANNAME.txt`
3. Alternative subdirs: `/path/to/bind/` or `/path/to/bindplans/`

### Error Handling

- Missing plan file → `plan_file_found: false`, empty arrays
- Unreadable file → Silently skipped
- Invalid format → Lines ignored, continues processing

## Performance Considerations

- Lazy loading: Plan files only loaded if `base_path` provided
- Efficient regex patterns for plan file scanning
- No external dependencies added
- Compatible with existing parser infrastructure

## Future Enhancements

Potential improvements:
- Support for compressed plan files (`.zip`, `.gz`)
- Plan file version tracking
- Dependency graphs between plans
- Statistics and reports generation
- Plan file caching for multiple references

## Backward Compatibility

✅ Fully backward compatible:
- Existing code continues to work unchanged
- New `bindplans` field is optional in output
- Optional parameters for plan file discovery
- No impact on existing extractors
