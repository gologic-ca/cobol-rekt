# BINDPLAN Extractor - Quick Start Guide

## 🎯 What You Can Now Do

Parse JCL files that contain **BINDPLAN** statements with automatic plan file discovery and parsing:

```jcl
//J59AIG   JOB (123456),'BATCH JOB'
//STEP1    EXEC BINDPLAN,PLAN=J59AIG
//STEP2    EXEC BINDPLAN,PLAN=J59ELF
```

The parser will:
1. **Identify** each BINDPLAN reference
2. **Find** the associated plan files (`.txt` files)
3. **Extract** INCLUDE OBJLIB and ENTRY statements
4. **Return** structured data with all the information

## ⚡ Quick Examples

### Example 1: Parse a JCL file

```python
from jcl_parser import JCLParser

parser = JCLParser()
result = parser.parse_file('myjob.jcl')

# Access BINDPLAN data
for bindplan in result['bindplans']:
    print(f"Plan: {bindplan['plan']}")
    print(f"Entry Point: {bindplan['entry_point']}")
    print(f"Included Files: {bindplan['include_files']}")
```

### Example 2: Parse JCL string with custom plan directory

```python
jcl_content = """//JOB1 JOB (123),'TEST'
//EXEC1 EXEC BINDPLAN,PLAN=MYPLAN
"""

parser = JCLParser()
result = parser.parse_string(jcl_content, base_path='/path/to/plans')

print(result['bindplans'])
```

### Example 3: Find shared components

```python
result = parser.parse_file('myjob.jcl')

# Build module dependency map
modules = {}
for bp in result['bindplans']:
    for mod in bp['include_files']:
        modules.setdefault(mod, []).append(bp['plan'])

# Find shared modules
shared = {m: p for m, p in modules.items() if len(p) > 1}
print(f"Shared modules: {shared}")
```

## 📁 File Organization

For best results, organize your files like this:

```
/project/
  myjob.jcl
  J59AIG.txt         ← Plan files in same directory
  J59ELF.txt
  J59ELN.txt
```

Or in subdirectories:

```
/project/
  myjob.jcl
  /plans/
    J59AIG.txt
    J59ELF.txt
    J59ELN.txt
```

## 📊 Output Structure

```python
{
    "job": {...},
    "steps": [...],
    "procedures": [...],
    "comments": [...],
    "bindplans": [
        {
            "name": "STEP1",              # Step name from JCL
            "plan": "J59AIG",             # Plan name from PLAN= parameter
            "line": 5,                    # Line number in JCL
            "entry_point": "J59AIG00",    # ENTRY from plan file
            "include_files": [            # INCLUDE OBJLIB from plan file
                "J59AIG00",
                "J59PTLF",
                "J59PRDF"
            ],
            "plan_file_path": "/path/...",
            "plan_file_found": true
        }
    ]
}
```

## 🔧 API Reference

### JCLParser.parse_file()

```python
result = parser.parse_file(
    file_path: str,              # Path to JCL file
    search_plan_files: bool=True # Auto-search for plan files
)
```

**Returns:** Dictionary with parsed JCL data including `bindplans`

### JCLParser.parse_string()

```python
result = parser.parse_string(
    jcl_content: str,                    # JCL content as string
    base_path: Optional[str] = None      # Path to search for plan files
)
```

**Returns:** Dictionary with parsed JCL data including `bindplans`

## 📝 Plan File Format

Your plan files should include these elements:

```
*
* Comments start with asterisk
*
   INCLUDE OBJLIB(PROGRAM1)    ← Object library includes
   INCLUDE OBJLIB(PROGRAM2)
   ENTRY MAINPROGRAM           ← Main entry point
   
   # Optional parameters
   AMODE=24
   RMODE=24
```

## ✅ Supported Syntax

The parser recognizes various spacing and formatting:

```
//STEP1 EXEC BINDPLAN,PLAN=MYPLAN
//STEP1 EXEC BINDPLAN, PLAN=MYPLAN
//STEP1 EXEC  BINDPLAN  ,  PLAN = MYPLAN
```

## 🚀 Running Examples

```bash
# Basic examples
python bindplan_examples.py

# User format test
python test_user_format.py

# Full test suite
python -m pytest tests/test_bindplan_extractor.py -v
```

## 📚 Documentation

- **BINDPLAN_EXTRACTOR.md** - Complete reference documentation
- **BINDPLAN_IMPLEMENTATION_SUMMARY.md** - Technical implementation details
- **bindplan_examples.py** - Working code examples
- **test_user_format.py** - Real-world format examples
- **tests/test_bindplan_extractor.py** - Test cases

## 🎁 Key Features

✅ **Automatic discovery** - Finds plan files automatically  
✅ **Smart parsing** - Extracts INCLUDE OBJLIB and ENTRY  
✅ **Flexible paths** - Searches multiple locations  
✅ **Error handling** - Gracefully handles missing files  
✅ **Backward compatible** - Works with existing code  
✅ **Case insensitive** - Handles any JCL case variant  

## 🔍 Finding Issues

If plan files aren't found:

1. Check the path exists
2. Verify file extension is `.txt`
3. Try using `parse_string()` with explicit `base_path`
4. Check that filename matches PLAN= parameter exactly

## 💡 Tips & Tricks

**Tip 1**: Always call `parse_file()` when possible - it auto-discovers plan files

**Tip 2**: Use `base_path` with `parse_string()` if files are in non-standard locations

**Tip 3**: Filter results to get only BINDPLAN steps with files found:
```python
found_plans = [bp for bp in result['bindplans'] if bp.get('plan_file_found')]
```

**Tip 4**: Extract all unique modules:
```python
all_mods = set()
for bp in result['bindplans']:
    all_mods.update(bp['include_files'])
```

## 📞 Support

For detailed documentation, see:
- API Reference: `BINDPLAN_EXTRACTOR.md`
- Implementation: `BINDPLAN_IMPLEMENTATION_SUMMARY.md`
- Examples: `bindplan_examples.py`

---

**Happy parsing! 🎉**
