# LegacyLens JCL Parser

A robust, extensible Python parser for JCL (Job Control Language) files with JSON output.

## Features

- Parse JCL files and extract structured information:
  - Job statements with parameters
  - Step definitions and their program references
  - DD statements with dataset names and parameters
  - Procedure definitions and calls
  - Comments and inline documentation
  - IF/THEN/ELSE conditional logic support
  - SYSIN inline data
- Output results in JSON format with customizable formatting
- Command-line interface with output and logging options
- Programmatic API for integration into other Python applications
- High-level interface with helper methods for data extraction
- Comprehensive logging system with configurable log levels
- Extensible architecture for adding custom parsing capabilities
- No external dependencies - uses only Python standard libraries
- Thoroughly tested with real-world JCL examples

## Installation

### From Source

```bash
git clone https://github.com/yourusername/legacylens-jcl-parser.git
cd legacylens-jcl-parser
pip install -e .
```

## Usage

### Command Line

```bash
# Parse a JCL file and print JSON to stdout
legacylens-jcl-parser path/to/your/jcl_file.jcl

# Parse a JCL file and save pretty-printed JSON to a file
legacylens-jcl-parser path/to/your/jcl_file.jcl --output result.json --pretty

# Set logging level
legacylens-jcl-parser path/to/your/jcl_file.jcl --log-level DEBUG
```

### As a Library

#### Basic Usage (JCLParser)

The JCL parser is designed to be easily integrated into your Python applications as a library.

```python
from jcl_parser import JCLParser

# Initialize the parser
parser = JCLParser()

# Parse a JCL file
jcl_data = parser.parse_file("path/to/your/jcl_file.jcl")

# Convert to JSON
json_output = parser.to_json(jcl_data, pretty=True)
print(json_output)

# Parse JCL from a string
jcl_string = """//JOBNAME JOB (ACCT),'TEST JOB',CLASS=A
//STEP1    EXEC PGM=IEFBR14
//DD1      DD   DSN=TEST.DATA,DISP=SHR"""
jcl_data = parser.parse_string(jcl_string)

# Save parsed data to a file
with open('output.json', 'w') as f:
    f.write(parser.to_json(jcl_data, pretty=True))
```

#### Enhanced Interface (JCLInterface)

For more convenience, you can use the JCLInterface class which provides helper methods for accessing parsed data:

```python
from jcl_parser import JCLInterface

# Initialize the interface
jcl = JCLInterface(log_level="INFO")  # Can be "INFO", "WARNING", or "DEBUG"

# Parse a JCL file
result = jcl.parse_file("path/to/your/jcl_file.jcl")

# Parse JCL from a string
jcl_string = """//JOBNAME JOB (ACCT),'TEST JOB',CLASS=A
//STEP1    EXEC PGM=IEFBR14
//DD1      DD   DSN=TEST.DATA,DISP=SHR"""
result = jcl.parse_string(jcl_string)

# Save to JSON file
jcl.save_to_file(result, "output.json", pretty=True)

# Access data using helper methods
job_name = jcl.get_job_name(result)
job_params = jcl.get_job_parameters(result)
steps = jcl.get_steps(result)

# Find a specific step
step = jcl.get_step_by_name(result, "STEP1")
if step:
    # Get program name from step
    program = jcl.get_step_program(step)
    
    # Get DD statements
    dd_statements = jcl.get_dd_statements(step)
    
    # Find specific DD statement
    dd = jcl.get_dd_by_name(step, "DD1")
    
# Get all procedures
procedures = jcl.get_procedures(result)

# Get all comments
comments = jcl.get_comments(result)
```

#### Extracting Data from Parsed Results

You can access specific elements from the parsed data structure:

```python
# Access job information
job_name = jcl_data["job"]["name"]
job_parameters = jcl_data["job"]["parameters"]

# Access steps
for step in jcl_data["steps"]:
    step_name = step["name"]
    step_program = step["parameters"].get("PGM")
    
    # Access DD statements in this step
    for dd in step["dd_statements"]:
        dd_name = dd["name"]
        dataset = dd["parameters"].get("DSN")
```

## Output Format

The parser generates a JSON structure with the following main components:

```json
{
  "job": {
    "name": "JOBNAME",
    "parameters": { /* parsed job parameters */ },
    "line": 1
  },
  "steps": [
    {
      "name": "STEP1",
      "parameters": { /* parsed step parameters */ },
      "dd_statements": [
        {
          "name": "DD1",
          "parameters": { /* parsed DD parameters */ },
          "line": 3
        }
      ],
      "line": 2
    }
  ],
  "procedures": [
    {
      "name": "PROC1",
      "parameters": { /* parsed procedure parameters */ },
      "steps": [ /* procedure steps */ ],
      "line": 10
    }
  ],
  "comments": [
    {
      "text": "This is a comment",
      "line": 5
    }
  ]
}
```

## Advanced Features

### Conditional Processing

The parser supports IF/THEN/ELSE conditional logic found in JCL files:

```
// IF (RC = 0) THEN
//STEP2A   EXEC PGM=IDCAMS
// ELSE
//STEP2B   EXEC PGM=IEFBR14
// ENDIF
```

### SYSIN Inline Data

Content within SYSIN DD * blocks is captured and preserved:

```
//SYSIN    DD *
  SORT FIELDS=(1,10,CH,A)
  INCLUDE COND=(21,5,CH,EQ,C'VALID')
/*
```

### Logging System

Configure logging verbosity based on your needs:

```python
from jcl_parser import JCLInterface, LogLevel

# Set log level during initialization
jcl = JCLInterface(log_level="DEBUG")

# Or change it later
jcl.set_log_level(LogLevel.WARNING)
```

## Extending the Parser

The parser is designed to be easily extended. To add support for new JCL statements or features:

1. Create a new extractor in the `extractors` directory
2. Implement the extraction logic using regex patterns
3. Integrate the new extractor in the main parser

See the `jcl_parser/extensions/example_extension.py` for an example of how to extend the parser with additional capabilities.

## License

MIT License

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.