#!/opt/homebrew/bin/bash

################################################################################
# scan_and_analyze_project.sh
# 
# Orchestrates complete project analysis pipeline:
# 1. Scans directory structure (CBL, JCL, CPY files) - supports modular projects
# 2. Creates CBL <-> JCL mappings (optional, AST generated even without JCL)
# 3. Generates aggregated ASTs for all programs
# 4. Builds dependency graph from ASTs and JCL relationships
#
# Usage:
#   # Named parameters (supports multiple directories per type, comma-separated)
#   bash scripts/scan_and_analyze_project.sh --cbl dir1,dir2 --jcl dir1,dir2 --cpy dir1,dir2 -o output [options]
#
#   # Legacy: Modular (auto-detect app-*/cbl/ structure)
#   bash scripts/scan_and_analyze_project.sh <root_dir> [output_dir] [options]
#
#   # Legacy: Flat (single directory per type)
#   bash scripts/scan_and_analyze_project.sh <cobol_dir> <jcl_dir> <cpy_dir> [output_dir] [options]
#
# Named Parameters:
#   --cbl <dirs>      Comma-separated list of COBOL source directories
#   --jcl <dirs>      Comma-separated list of JCL directories
#   --cpy <dirs>      Comma-separated list of copybook directories
#   -o, --output      Output directory (default: ./out)
#
# Options:
#   -g, --graph       Generate dependency graphs (JSON + SVG) - disabled by default
#   -m, --metrics     Generate performance metrics report - disabled by default
#
# Examples:
#   # Multiple directories per type
#   bash scripts/scan_and_analyze_project.sh --cbl app1/cbl,app2/cbl --jcl jcl/,jcl2/ --cpy cpy/,cpy-bms/ -o ./out
#
#   # Single directories (equivalent to flat mode)
#   bash scripts/scan_and_analyze_project.sh --cbl src/cobol --jcl src/jcl --cpy src/cpy -o ./out
#
#   # Modular structure (auto-detect)
#   bash scripts/scan_and_analyze_project.sh /path/to/aws-mainframe ./out
#
#   # Flat structure (legacy)
#   bash scripts/scan_and_analyze_project.sh app/cbl app/jcl app/cpy ./out -g -m
################################################################################

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Arrays for multiple directories
declare -a CBL_DIRS=()
declare -a JCL_DIRS=()
declare -a CPY_DIRS=()
OUTPUT_DIR=""
GENERATE_GRAPHS=false
GENERATE_METRICS=false
MODE=""

# ============================================================================
# ARGUMENT PARSING
# ============================================================================

# Detect if named parameters are used (--cbl, --jcl, --cpy)
USES_NAMED_PARAMS=false
for arg in "$@"; do
    if [[ "$arg" == "--cbl" ]] || [[ "$arg" == "--jcl" ]] || [[ "$arg" == "--cpy" ]]; then
        USES_NAMED_PARAMS=true
        break
    fi
done

if [[ "$USES_NAMED_PARAMS" == "true" ]]; then
    # ========================================================================
    # NAMED PARAMETER MODE: --cbl dir1,dir2 --jcl dir1,dir2 --cpy dir1,dir2
    # ========================================================================
    MODE="MULTI"
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --cbl)
                shift
                IFS=',' read -ra _dirs <<< "$1"
                for d in "${_dirs[@]}"; do
                    [[ -n "$d" ]] && CBL_DIRS+=("$d")
                done
                ;;
            --jcl)
                shift
                IFS=',' read -ra _dirs <<< "$1"
                for d in "${_dirs[@]}"; do
                    [[ -n "$d" ]] && JCL_DIRS+=("$d")
                done
                ;;
            --cpy)
                shift
                IFS=',' read -ra _dirs <<< "$1"
                for d in "${_dirs[@]}"; do
                    [[ -n "$d" ]] && CPY_DIRS+=("$d")
                done
                ;;
            -o|--output)
                shift
                OUTPUT_DIR="$1"
                ;;
            -g|--graph)
                GENERATE_GRAPHS=true
                ;;
            -m|--metrics)
                GENERATE_METRICS=true
                ;;
            *)
                echo -e "${YELLOW}Warning: Unknown argument: $1${NC}"
                ;;
        esac
        shift
    done
    
    OUTPUT_DIR="${OUTPUT_DIR:-./out}"
    
    # Validate at least CBL dirs provided
    if [[ ${#CBL_DIRS[@]} -eq 0 ]]; then
        echo -e "${RED}ERROR: No COBOL directories specified (use --cbl dir1,dir2)${NC}"
        exit 1
    fi
    
else
    # ========================================================================
    # LEGACY POSITIONAL MODE (backward compatible)
    # ========================================================================
    
    ROOT_DIR="$1"
    
    if [[ -z "$ROOT_DIR" ]]; then
        echo -e "${RED}ERROR: Missing root directory${NC}"
        echo ""
        echo "Usage:"
        echo "  # Named parameters (multiple dirs per type):"
        echo "  $0 --cbl dir1,dir2 --jcl dir1,dir2 --cpy dir1,dir2 -o output [options]"
        echo ""
        echo "  # Legacy positional (single dir per type):"
        echo "  $0 <root_dir> [output_dir] [options]"
        echo "  $0 <cobol_dir> <jcl_dir> <cpy_dir> [output_dir] [options]"
        exit 1
    fi
    
    # Check if this is a flat structure (all 3 different args provided)
    if [[ -n "$2" ]] && [[ -n "$3" ]] && [[ ! "$2" =~ ^- ]] && [[ ! "$3" =~ ^- ]] && \
       [[ "$1" != "$2" || "$2" != "$3" ]]; then
        cbl_in_arg1=$(find "$1" -maxdepth 1 -iname "*.cbl" -type f 2>/dev/null | wc -l)
        jcl_in_arg2=$(find "$2" -maxdepth 1 -iname "*.jcl" -type f 2>/dev/null | wc -l)
        cpy_in_arg3=$(find "$3" -maxdepth 1 -iname "*.cpy" -type f 2>/dev/null | wc -l)
        
        if [[ $cbl_in_arg1 -gt 0 ]] || [[ $jcl_in_arg2 -gt 0 ]] || [[ $cpy_in_arg3 -gt 0 ]]; then
            CBL_DIRS=("$1")
            JCL_DIRS=("$2")
            CPY_DIRS=("$3")
            OUTPUT_DIR="${4:-./out}"
            MODE="FLAT"
        else
            CBL_DIRS=("$1")
            JCL_DIRS=("$1")
            CPY_DIRS=("$1")
            OUTPUT_DIR="${2:-./out}"
            MODE="MODULAR"
        fi
    else
        CBL_DIRS=("$1")
        JCL_DIRS=("$1")
        CPY_DIRS=("$1")
        OUTPUT_DIR="${2:-./out}"
        MODE="MODULAR"
        
        if [[ -n "$4" ]] && [[ ! "$4" =~ ^- ]]; then
            OUTPUT_DIR="$4"
        fi
    fi
    
    # Parse optional flags from remaining positional args
    if [[ "$MODE" == "FLAT" ]]; then
        START_ARG=5
    else
        if [[ -n "$3" ]] && [[ "$3" =~ ^- ]]; then
            START_ARG=3
        elif [[ -n "$4" ]] && [[ "$4" =~ ^- ]]; then
            START_ARG=4
        else
            START_ARG=5
        fi
    fi
    
    for arg in "${@:$START_ARG}"; do
        case "$arg" in
            -g|--graph)
                GENERATE_GRAPHS=true
                ;;
            -m|--metrics)
                GENERATE_METRICS=true
                ;;
        esac
    done
fi

# For convenience, keep a single "primary" reference for each type (first dir in list)
COBOL_DIR="${CBL_DIRS[0]}"
JCL_DIR="${JCL_DIRS[0]:-}"
CPY_DIR="${CPY_DIRS[0]:-}"

# Validate inputs: all specified directories must exist
for dir in "${CBL_DIRS[@]}"; do
    [[ -d "$dir" ]] || { echo -e "${RED}ERROR: COBOL directory not found: $dir${NC}"; exit 1; }
done
for dir in "${JCL_DIRS[@]}"; do
    [[ -d "$dir" ]] || { echo -e "${RED}ERROR: JCL directory not found: $dir${NC}"; exit 1; }
done
for dir in "${CPY_DIRS[@]}"; do
    [[ -d "$dir" ]] || { echo -e "${RED}ERROR: Copybook directory not found: $dir${NC}"; exit 1; }
done

mkdir -p "$OUTPUT_DIR"

echo -e "${BLUE}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     COBOL Project Analysis & AST Generation          ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════╝${NC}"
echo ""

# ============================================================================
# STEP 1: SCAN PROJECT STRUCTURE
# ============================================================================

echo -e "${BLUE}[Step 1/4] Scanning project structure${NC}"
echo "  Mode:       $MODE"
echo "  CBL dirs:   ${CBL_DIRS[*]}"
echo "  JCL dirs:   ${JCL_DIRS[*]:-<none>}"
echo "  CPY dirs:   ${CPY_DIRS[*]:-<none>}"
echo "  Output:     $OUTPUT_DIR"
echo ""

# Find all CBL files with their module context
declare -A CBL_FILES_MAP  # Map: CBL_PATH -> MODULE_PATH
declare -A CBL_MODULE_MAP # Map: CBL_NAME -> MODULE_PATH

if [[ "$MODE" == "MODULAR" ]]; then
    # Find all CBL files in modular structure (app-*/cbl/, cbl/)
    for cbl_dir in "${CBL_DIRS[@]}"; do
        for cbl_file in $(find "$cbl_dir" -ipath "*/cbl/*.cbl" -type f); do
            cbl_name=$(basename "$cbl_file" | sed 's/\.[cC][bB][lL]$//')
            module_path=$(echo "$cbl_file" | sed 's|/cbl/[^/]*$||')
            CBL_FILES_MAP["$cbl_file"]="$module_path"
            CBL_MODULE_MAP["$cbl_name"]="$module_path"
        done
    done
else
    # FLAT or MULTI mode: find CBL files in all specified directories
    for cbl_dir in "${CBL_DIRS[@]}"; do
        for cbl_file in $(find "$cbl_dir" -maxdepth 1 -iname "*.cbl" -type f); do
            cbl_name=$(basename "$cbl_file" | sed 's/\.[cC][bB][lL]$//')
            CBL_FILES_MAP["$cbl_file"]="$cbl_dir"
            CBL_MODULE_MAP["$cbl_name"]="$cbl_dir"
        done
    done
fi

CBL_COUNT=${#CBL_FILES_MAP[@]}

# Count JCL files across all JCL directories
JCL_COUNT=0
for jcl_dir in "${JCL_DIRS[@]}"; do
    dir_jcl_count=$(find "$jcl_dir" -iname "*.jcl" -type f 2>/dev/null | wc -l)
    ((JCL_COUNT += dir_jcl_count))
done

# ============================================================================
# DISCOVER ALL COPYBOOK DIRECTORIES
# ============================================================================
# Aggregate all copybook files from specified CPY_DIRS (and auto-discover
# additional cpy-* directories in MODULAR mode)

declare -a ALL_CPY_DIRS
AGGREGATED_CPY_DIR=""

if [[ "$MODE" == "MULTI" ]] || [[ "$MODE" == "FLAT" ]]; then
    # Explicit directories: use them directly
    ALL_CPY_DIRS=("${CPY_DIRS[@]}")
    
    # Also discover additional cpy-* subdirectories within each specified dir's parent
    for cpy_dir in "${CPY_DIRS[@]}"; do
        CPY_PARENT_DIR=$(dirname "$cpy_dir")
        while IFS= read -r -d '' dir; do
            [[ "$dir" == "$cpy_dir" ]] && continue
            # Already in list?
            for existing in "${ALL_CPY_DIRS[@]}"; do
                [[ "$existing" == "$dir" ]] && continue 2
            done
            cpy_in_dir=$(find "$dir" -maxdepth 1 \( -iname "*.cpy" -o -iname "*.copy" \) -type f 2>/dev/null | head -1)
            if [[ -n "$cpy_in_dir" ]]; then
                ALL_CPY_DIRS+=("$dir")
            fi
        done < <(find "$CPY_PARENT_DIR" -type d \( -iname "cpy-*" \) -print0 2>/dev/null)
    done
else
    # MODULAR mode: auto-discover from root
    CPY_PARENT_DIR="${CPY_DIRS[0]}"
    
    # Add main CPY_DIR if it contains copybooks
    main_cpy_count=$(find "$CPY_DIR" -maxdepth 1 \( -iname "*.cpy" -o -iname "*.copy" \) -type f 2>/dev/null | head -1)
    if [[ -n "$main_cpy_count" ]]; then
        ALL_CPY_DIRS+=("$CPY_DIR")
    fi
    
    # Search for all copybook directories recursively
    while IFS= read -r -d '' dir; do
        [[ "$dir" == "$CPY_DIR" ]] && continue
        cpy_in_dir=$(find "$dir" -maxdepth 1 \( -iname "*.cpy" -o -iname "*.copy" \) -type f 2>/dev/null | head -1)
        if [[ -n "$cpy_in_dir" ]]; then
            ALL_CPY_DIRS+=("$dir")
        fi
    done < <(find "$CPY_PARENT_DIR" -type d \( -iname "cpy" -o -iname "cpy-*" -o -iname "copy" -o -iname "copybook*" -o -iname "include*" \) -print0 2>/dev/null)
    
    # Also check for copybooks without extension
    while IFS= read -r -d '' dir; do
        [[ "$dir" == "$CPY_DIR" ]] && continue
        for existing in "${ALL_CPY_DIRS[@]}"; do
            [[ "$existing" == "$dir" ]] && continue 2
        done
        potential_cpy=$(find "$dir" -maxdepth 1 -type f ! -name "*.*" -name "[A-Z]*" 2>/dev/null | head -1)
        if [[ -n "$potential_cpy" ]]; then
            ALL_CPY_DIRS+=("$dir")
        fi
    done < <(find "$CPY_PARENT_DIR" -type d \( -iname "cpy" -o -iname "cpy-*" -o -iname "copy" -o -iname "copybook*" \) -print0 2>/dev/null)
fi

echo "  Scanning for copybook directories..."

# Count total copybooks across all directories
CPY_COUNT=0
for cpy_dir in "${ALL_CPY_DIRS[@]}"; do
    dir_count=$(find "$cpy_dir" -maxdepth 1 \( -iname "*.CPY" -o -iname "*.COPY" -o \( -type f ! -name "*.*" -name "[A-Z]*" \) \) 2>/dev/null | wc -l)
    ((CPY_COUNT += dir_count))
done

# Aggregate all copybook directories into a single temp directory for the parser
if [[ ${#ALL_CPY_DIRS[@]} -gt 1 ]]; then
    AGGREGATED_CPY_DIR=$(mktemp -d)
    echo "  Found ${#ALL_CPY_DIRS[@]} copybook directories:"
    
    for cpy_dir in "${ALL_CPY_DIRS[@]}"; do
        dir_name=$(basename "$cpy_dir")
        dir_count=$(find "$cpy_dir" -maxdepth 1 \( -iname "*.CPY" -o -iname "*.COPY" -o \( -type f ! -name "*.*" -name "[A-Z]*" \) \) 2>/dev/null | wc -l)
        echo "    • $dir_name/ ($dir_count files)"
        find "$cpy_dir" -maxdepth 1 \( -iname "*.cpy" -o -iname "*.copy" -o \( -type f ! -name "*.*" -name "[A-Z]*" \) \) -exec cp {} "$AGGREGATED_CPY_DIR/" \; 2>/dev/null
    done
    ORIGINAL_CPY_DIR="$CPY_DIR"
    CPY_DIR="$AGGREGATED_CPY_DIR"
elif [[ ${#ALL_CPY_DIRS[@]} -eq 1 ]]; then
    echo "  Found 1 copybook directory: $(basename "${ALL_CPY_DIRS[0]}")/"
    CPY_DIR="${ALL_CPY_DIRS[0]}"
elif [[ ${#ALL_CPY_DIRS[@]} -eq 0 ]]; then
    # Fallback: search recursively for any .cpy files in all CPY_DIRS parents
    for cpy_dir in "${CPY_DIRS[@]}"; do
        parent_dir=$(dirname "$cpy_dir")
        fallback_count=$(find "$parent_dir" -iname "*.cpy" -o -iname "*.copy" -type f 2>/dev/null | wc -l)
        if [[ $fallback_count -gt 0 ]]; then
            if [[ -z "$AGGREGATED_CPY_DIR" ]]; then
                AGGREGATED_CPY_DIR=$(mktemp -d)
            fi
            find "$parent_dir" \( -iname "*.cpy" -o -iname "*.copy" \) -type f -exec cp {} "$AGGREGATED_CPY_DIR/" \; 2>/dev/null
            ((CPY_COUNT += fallback_count))
        fi
    done
    if [[ -n "$AGGREGATED_CPY_DIR" ]]; then
        CPY_DIR="$AGGREGATED_CPY_DIR"
        echo "  Found $CPY_COUNT copybooks (scattered across directories)"
    fi
fi

echo "  Found: $CBL_COUNT CBL files, $JCL_COUNT JCL files, $CPY_COUNT copybooks"
echo ""

# ============================================================================
# STEP 2: CREATE CBL-JCL MAPPINGS (Using Python script for accurate matching)
# ============================================================================

echo -e "${BLUE}[Step 2/4] Creating CBL-JCL mappings${NC}"

MAPPINGS_FILE="$OUTPUT_DIR/project-analysis.json"
mkdir -p "$OUTPUT_DIR"

JCL_ANALYSIS_SCRIPT="$SCRIPT_DIR/analyze_jcl_to_cobol.py"

# Check for virtual environment with jcl-parser installed
VENV_PYTHON=""
if [[ -f "$PROJECT_ROOT/.venv/bin/python" ]]; then
    VENV_PYTHON="$PROJECT_ROOT/.venv/bin/python"
elif [[ -f "$PROJECT_ROOT/.venv/Scripts/python.exe" ]]; then
    # Windows
    VENV_PYTHON="$PROJECT_ROOT/.venv/Scripts/python.exe"
fi

# Try to find Python (prefer venv with jcl-parser)
PYTHON_CMD=""
if [[ -n "$VENV_PYTHON" ]]; then
    PYTHON_CMD="$VENV_PYTHON"
    echo "  Using venv Python with AST-based JCL parser"
else
    for cmd in python python3 python2; do
        if command -v "$cmd" &>/dev/null 2>&1; then
            if "$cmd" --version &>/dev/null 2>&1; then
                PYTHON_CMD="$cmd"
                break
            fi
        fi
    done
    if [[ -n "$PYTHON_CMD" ]]; then
        echo "  Using system Python (regex fallback for JCL parsing)"
    fi
fi

if [[ -n "$PYTHON_CMD" ]] && [[ -f "$JCL_ANALYSIS_SCRIPT" ]]; then
    # Convert paths to Windows format for Python if on Windows/Git Bash
    # For multiple JCL dirs, aggregate into a temp directory for the Python script
    PYTHON_JCL_DIR=""
    TEMP_JCL_AGGREGATE=""
    
    if [[ ${#JCL_DIRS[@]} -gt 1 ]]; then
        # Multiple JCL dirs: aggregate into temp directory
        TEMP_JCL_AGGREGATE=$(mktemp -d)
        for jcl_d in "${JCL_DIRS[@]}"; do
            find "$jcl_d" -iname "*.jcl" -type f -exec cp {} "$TEMP_JCL_AGGREGATE/" \; 2>/dev/null
        done
        PYTHON_JCL_DIR="$TEMP_JCL_AGGREGATE"
    else
        PYTHON_JCL_DIR="${JCL_DIRS[0]:-}"
    fi
    
    # Similarly aggregate COBOL dirs for the Python script
    PYTHON_COBOL_DIR=""
    TEMP_CBL_AGGREGATE=""
    
    if [[ ${#CBL_DIRS[@]} -gt 1 ]]; then
        TEMP_CBL_AGGREGATE=$(mktemp -d)
        for cbl_d in "${CBL_DIRS[@]}"; do
            find "$cbl_d" -iname "*.cbl" -type f -exec cp {} "$TEMP_CBL_AGGREGATE/" \; 2>/dev/null
        done
        PYTHON_COBOL_DIR="$TEMP_CBL_AGGREGATE"
    else
        PYTHON_COBOL_DIR="${CBL_DIRS[0]}"
    fi
    
    # Windows path conversion
    if [[ "$PYTHON_JCL_DIR" =~ ^/c/ ]]; then
        PYTHON_JCL_DIR=$(echo "$PYTHON_JCL_DIR" | sed 's|^/c/|C:/|' | sed 's|/|\\|g')
    fi
    if [[ "$PYTHON_COBOL_DIR" =~ ^/c/ ]]; then
        PYTHON_COBOL_DIR=$(echo "$PYTHON_COBOL_DIR" | sed 's|^/c/|C:/|' | sed 's|/|\\|g')
    fi
    
    # Use Python script for accurate JCL-COBOL matching via PGM= extraction
    TEMP_OUTPUT=$(mktemp)

    echo "$PYTHON_CMD $JCL_ANALYSIS_SCRIPT -j $PYTHON_JCL_DIR -c $PYTHON_COBOL_DIR --json > $TEMP_OUTPUT"

    "$PYTHON_CMD" "$JCL_ANALYSIS_SCRIPT" \
        -j "$PYTHON_JCL_DIR" \
        -c "$PYTHON_COBOL_DIR" \
        --json > "$TEMP_OUTPUT" 2>&1
    PYTHON_EXIT=$?
    
    if [[ $PYTHON_EXIT -eq 0 ]] && [[ -s "$TEMP_OUTPUT" ]]; then
        cp "$TEMP_OUTPUT" "$MAPPINGS_FILE"
        #rm "$TEMP_OUTPUT"
        
        # Get count before any further processing
        MAPPED=$(grep -c '"program"' "$MAPPINGS_FILE" 2>/dev/null || echo "0")
        echo "  Matched: $MAPPED programs using PGM= extraction"

        MAPPED=$(grep -c '"bindplans"' "$MAPPINGS_FILE" 2>/dev/null || echo "0")
        echo "  Matched: $MAPPED programs using BINDPLAN extraction"
    else
        echo -e "${YELLOW}  Warning: Python script analysis failed${NC}"
        echo "{}" > "$MAPPINGS_FILE"
        MAPPED=0
    fi
    
    # Clean up temporary aggregate directories
    [[ -n "$TEMP_JCL_AGGREGATE" && -d "$TEMP_JCL_AGGREGATE" ]] && rm -rf "$TEMP_JCL_AGGREGATE"
    [[ -n "$TEMP_CBL_AGGREGATE" && -d "$TEMP_CBL_AGGREGATE" ]] && rm -rf "$TEMP_CBL_AGGREGATE"
else
    if [[ -z "$PYTHON_CMD" ]]; then
        echo -e "${YELLOW}  Warning: Python not found${NC}"
    fi
    echo "{}" > "$MAPPINGS_FILE"
    MAPPED=0
fi

echo ""

# ============================================================================
# STEP 3: GENERATE AGGREGATED ASTs
# ============================================================================

echo -e "${BLUE}[Step 3/4] Generating aggregated ASTs${NC}"

REPORT_DIR="$OUTPUT_DIR/report"
mkdir -p "$REPORT_DIR"

# Check if smojol-cli JAR is available
JAR_PATH="$PROJECT_ROOT/smojol-cli/target/smojol-cli.jar"
AST_COUNT=0

# Timing and statistics
AST_START_TIME=$(date +%s%N)  # Nanoseconds for better precision
TOTAL_LOC=0

# Per-file metrics array
declare -a FILE_METRICS  # Array to store metrics for each file

# Load JCL-CBL mappings from Step 2
declare -A JCL_MAPPINGS
if [[ -f "$MAPPINGS_FILE" ]]; then
    # Parse JSON to extract JCL files for each program
    # Using Python for reliable JSON parsing
    PYTHON_CMD=""
    for cmd in python python3; do
        if command -v "$cmd" &>/dev/null 2>&1; then
            PYTHON_CMD="$cmd"
            break
        fi
    done
    
    if [[ -n "$PYTHON_CMD" ]]; then
        # Convert path to Windows format for Python if on Windows/Git Bash
        PYTHON_MAPPING_FILE="$MAPPINGS_FILE"
        if [[ "$MAPPINGS_FILE" =~ ^/c/ ]]; then
            # Convert /c/path to C:\path for Python
            PYTHON_MAPPING_FILE=$(echo "$MAPPINGS_FILE" | sed 's|^/c/|C:/|' | sed 's|/|\\|g')
        fi
        
        # Load mappings: program -> jcl_files array
        eval "$("$PYTHON_CMD" -c "
import json
with open(r'$PYTHON_MAPPING_FILE', 'r') as f:
    data = json.load(f)
for prog_data in data.get('cbl_files', []):
    prog = prog_data['program'].upper()
    jcl_list = prog_data.get('jcl_files', [])
    if jcl_list:
        jcls = '|'.join(jcl_list)
        print(f'JCL_MAPPINGS[{prog}]=\"{jcls}\"')
" 2>/dev/null)"
    fi
fi

if [[ -f "$JAR_PATH" ]] && command -v java &>/dev/null; then
    # CPY_DIR is already set to the aggregated copybook directory (if multiple dirs found)
    # or the original directory (if single dir)
    CPY_SEARCH_DIR="$CPY_DIR"
    
    # Use smojol-cli to generate proper ASTs with WRITE_AGGREGATED_JCL_AST
    
    for cbl_file in "${!CBL_FILES_MAP[@]}"; do
        if [[ ! -f "$cbl_file" ]]; then continue; fi
        
        cbl_basename=$(basename "$cbl_file")
        cbl_name=$(echo "$cbl_basename" | sed 's/\.[cC][bB][lL]$//')
        cbl_name_upper=$(echo "$cbl_name" | tr '[:lower:]' '[:upper:]')
        module_path="${CBL_FILES_MAP[$cbl_file]}"
        
        # Count lines of code for this file
        file_loc=$(wc -l < "$cbl_file" 2>/dev/null || echo 0)
        ((TOTAL_LOC += file_loc))
        
        # Get file size in bytes
        file_size=$(stat -f%z "$cbl_file" 2>/dev/null || stat -c%s "$cbl_file" 2>/dev/null || echo 0)
        
        # Start timing for this file
        FILE_START=$(date +%s%N)
        
        # Copybooks: use the pre-aggregated CPY_SEARCH_DIR
        # (already contains all copybooks from cpy/, cpy-bms/, etc.)
        TEMP_CPY_DIR=""
        
        # Step 2: Find JCL for this file (optional)
        # Priority: module's jcl/ > mappings > all JCL_DIRS
        PROGRAM_JCL_DIR="${JCL_DIRS[0]:-}"
        TEMP_PROGRAM_JCL_DIR=""
        JCL_FOUND=0
        
        if [[ "$MODE" == "MODULAR" ]]; then
            MODULE_JCL_DIR="$module_path/jcl"
            if [[ -d "$MODULE_JCL_DIR" ]]; then
                # Check for matching JCL file in module
                matching_jcl=$(find "$MODULE_JCL_DIR" -iname "*$cbl_name*" -iname "*.jcl" -type f 2>/dev/null | head -1)
                if [[ -f "$matching_jcl" ]]; then
                    TEMP_PROGRAM_JCL_DIR=$(mktemp -d)
                    cp "$matching_jcl" "$TEMP_PROGRAM_JCL_DIR/" 2>/dev/null
                    PROGRAM_JCL_DIR="$TEMP_PROGRAM_JCL_DIR"
                    JCL_FOUND=1
                fi
            fi
        fi
        
        # If no module JCL, check global mappings (search across all JCL_DIRS)
        if [[ $JCL_FOUND -eq 0 ]] && [[ -n "${JCL_MAPPINGS[$cbl_name_upper]}" ]]; then
            TEMP_PROGRAM_JCL_DIR=$(mktemp -d)
            IFS='|' read -ra jcl_files <<< "${JCL_MAPPINGS[$cbl_name_upper]}"
            
            for jcl_file_path in "${jcl_files[@]}"; do
                full_jcl_path=""
                
                # Search across all JCL directories
                for jcl_search_dir in "${JCL_DIRS[@]}"; do
                    candidate="$jcl_search_dir/$jcl_file_path"
                    if [[ -f "$candidate" ]]; then
                        full_jcl_path="$candidate"
                        break
                    fi
                    # Also try with .jcl extension if the stem has no extension
                    if [[ "$jcl_file_path" != *.* ]] && [[ -f "${candidate}.jcl" ]]; then
                        full_jcl_path="${candidate}.jcl"
                        break
                    fi
                done
                
                # If not found by direct path, search by basename across all dirs
                if [[ -z "$full_jcl_path" ]]; then
                    jcl_basename=$(basename "$jcl_file_path")
                    for jcl_search_dir in "${JCL_DIRS[@]}"; do
                        found=$(find "$jcl_search_dir" -iname "$jcl_basename" -type f 2>/dev/null | head -1)
                        if [[ -n "$found" ]]; then
                            full_jcl_path="$found"
                            break
                        fi
                        # Also try with .jcl extension if the stem has no extension
                        if [[ "$jcl_basename" != *.* ]]; then
                            found=$(find "$jcl_search_dir" -iname "${jcl_basename}.jcl" -type f 2>/dev/null | head -1)
                            if [[ -n "$found" ]]; then
                                full_jcl_path="$found"
                                break
                            fi
                        fi
                    done
                fi
                
                if [[ -n "$full_jcl_path" && -f "$full_jcl_path" ]]; then
                    cp "$full_jcl_path" "$TEMP_PROGRAM_JCL_DIR/" 2>/dev/null || true
                    ((JCL_FOUND++))
                fi
            done
            
            if [[ $JCL_FOUND -gt 0 ]]; then
                PROGRAM_JCL_DIR="$TEMP_PROGRAM_JCL_DIR"
            fi
        fi

        # If no JCL was found via mappings or module, use an empty temp dir so
        # Java does not fall back to picking a random file from the full JCL dir.
        if [[ $JCL_FOUND -eq 0 ]]; then
            TEMP_PROGRAM_JCL_DIR=$(mktemp -d)
            PROGRAM_JCL_DIR="$TEMP_PROGRAM_JCL_DIR"
        fi

        # Step 3: Generate AST (with or without JCL)
        # Use the module's cbl directory for modular, or the file's directory for flat
        if [[ "$MODE" == "MODULAR" ]]; then
            # For modular mode, always use the cbl subdirectory of the module
            CBL_SOURCE_DIR="$module_path/cbl"
        else
            CBL_SOURCE_DIR="$(dirname "$cbl_file")"
        fi
        
        ERROR_LOG=$(mktemp)

        # Check if report already exists
        report_dir="$REPORT_DIR/$cbl_name.cbl.report"
        if [[ -d "$report_dir" ]]; then
            echo "    [SKIP] Report already exists for $cbl_name ($report_dir)"
        else
        echo "    [EXECUTE JAVA] $cbl_file"
        if java -jar "$JAR_PATH" run \
            -c WRITE_AGGREGATED_JCL_AST \
            -j "$PROGRAM_JCL_DIR" \
            -s "$CBL_SOURCE_DIR" \
            -cp "$CPY_SEARCH_DIR" \
            -r "$REPORT_DIR" \
            "$cbl_basename" > "$ERROR_LOG" 2>&1; then
            ((AST_COUNT++))
            FILE_STATUS="✓"
            echo "$ERROR_LOG"
        else
            # AST generation failed - create fallback AST with copybook extraction
            FILE_STATUS="✓ (partial)"
            
            # Extract copybooks from source file
            COPYBOOKS_JSON="[]"
            if [[ -f "$cbl_file" ]]; then
                # Extract COPY statements (case-insensitive, handles line numbers, COPY XXX., COPY 'XXX', COPY "XXX", etc.)
                COPYBOOK_NAMES=$(grep -iE "^[0-9 ]*COPY[[:space:]]+" "$cbl_file" | \
                    sed -E "s/^[0-9 ]*COPY[[:space:]]+['\"]*([A-Z0-9_-]+)['\"]*\.?.*/\1/i" | \
                    grep -v "^[0-9]*$" | \
                    sort -u)
                
                if [[ -n "$COPYBOOK_NAMES" ]]; then
                    # Build JSON array
                    COPYBOOKS_JSON="["
                    FIRST_CPY=true
                    while IFS= read -r cpy_name; do
                        [[ -z "$cpy_name" ]] && continue
                        if [[ "$FIRST_CPY" == "true" ]]; then
                            FIRST_CPY=false
                        else
                            COPYBOOKS_JSON+=","
                        fi
                        COPYBOOKS_JSON+="\"$cpy_name\""
                    done <<< "$COPYBOOK_NAMES"
                    COPYBOOKS_JSON+="]"
                fi
            fi
            
            # Create AST directory structure
            ast_dir="$REPORT_DIR/$cbl_basename.report/ast/aggregated"
            mkdir -p "$ast_dir"
            
            # Generate fallback AST JSON compatible with API (Option A)
            cat > "$ast_dir/$cbl_name-aggregated.json" <<EOF
{
  "nodeType": "StartRuleContext",
  "text": "COBOL program stub - full AST generation failed (copybooks unresolved)",
  "programName": "$cbl_name",
  "path": "$cbl_file",
  "program_id": "$cbl_name",
  "copybooks": $COPYBOOKS_JSON,
  "copybooksMetadata": {},
  "datasets": [],
  "call_flow": {
    "callees": [],
    "callers": []
  },
  "children": [
    {
      "nodeType": "CompilationUnitContext",
      "text": "Fallback AST - copybooks could not be resolved",
      "copybooks": $COPYBOOKS_JSON,
      "children": []
    }
  ]
}
EOF
            ((AST_COUNT++))
            rm "$ERROR_LOG"
        fi
     fi   
        # End timing for this file
        FILE_END=$(date +%s%N)
        FILE_DURATION_MS=$(( (FILE_END - FILE_START) / 1000000 ))
        
        # Store metrics for later reporting
        FILE_METRICS+=("$cbl_name|$file_loc|$file_size|$FILE_DURATION_MS|$FILE_STATUS")
        
        # Clean up temporary directories
        [[ -n "$TEMP_PROGRAM_JCL_DIR" && -d "$TEMP_PROGRAM_JCL_DIR" ]] && rm -rf "$TEMP_PROGRAM_JCL_DIR"
    done
    
    # Clean up aggregated copybook directory if we created one
    [[ -n "$AGGREGATED_CPY_DIR" && -d "$AGGREGATED_CPY_DIR" ]] && rm -rf "$AGGREGATED_CPY_DIR"
else
    # Count lines of code even for fallback
    for cbl_file in "${!CBL_FILES_MAP[@]}"; do
        file_loc=$(wc -l < "$cbl_file" 2>/dev/null || echo 0)
        ((TOTAL_LOC += file_loc))
    done
    
    # Fallback: Generate minimal AST structure if JAR unavailable
    if [[ ! -f "$JAR_PATH" ]]; then
        echo -e "${YELLOW}  Warning: smojol-cli.jar not found${NC}"
    fi
    if ! command -v java &>/dev/null; then
        echo -e "${YELLOW}  Warning: Java not available${NC}"
    fi
    echo "  Generating minimal AST structure as fallback"
    
    for cbl_file in "${!CBL_FILES_MAP[@]}"; do
        if [[ ! -f "$cbl_file" ]]; then continue; fi
        
        cbl_basename=$(basename "$cbl_file")
        cbl_name=$(echo "$cbl_basename" | sed 's/\.[cC][bB][lL]$//')
        file_loc=$(wc -l < "$cbl_file" 2>/dev/null || echo 0)
        file_size=$(stat -f%z "$cbl_file" 2>/dev/null || stat -c%s "$cbl_file" 2>/dev/null || echo 0)
        
        # Timing
        FILE_START=$(date +%s%N)
        
        # Extract copybooks from source file
        COPYBOOKS_JSON="[]"
        if [[ -f "$cbl_file" ]]; then
            COPYBOOK_NAMES=$(grep -iE "^[0-9 ]*COPY[[:space:]]+" "$cbl_file" | \
                sed -E "s/^[0-9 ]*COPY[[:space:]]+['\"]*([A-Z0-9_-]+)['\"]*\.?.*/\1/i" | \
                grep -v "^[0-9]*$" | \
                sort -u)
            
            if [[ -n "$COPYBOOK_NAMES" ]]; then
                COPYBOOKS_JSON="["
                FIRST_CPY=true
                while IFS= read -r cpy_name; do
                    [[ -z "$cpy_name" ]] && continue
                    if [[ "$FIRST_CPY" == "true" ]]; then
                        FIRST_CPY=false
                    else
                        COPYBOOKS_JSON+=","
                    fi
                    COPYBOOKS_JSON+="\"$cpy_name\""
                done <<< "$COPYBOOK_NAMES"
                COPYBOOKS_JSON+="]"
            fi
        fi
        
        FILE_END=$(date +%s%N)
        FILE_DURATION_MS=$(( (FILE_END - FILE_START) / 1000000 ))
        
        ast_dir="$REPORT_DIR/$cbl_basename.report/ast/aggregated"
        mkdir -p "$ast_dir"
        
        # Create fallback AST JSON compatible with API (Option A)
        cat > "$ast_dir/$cbl_name-aggregated.json" <<EOF
{
  "nodeType": "StartRuleContext",
  "text": "COBOL program stub - full AST generation requires smojol-cli JAR",
  "programName": "$cbl_name",
  "path": "$cbl_file",
  "program_id": "$cbl_name",
  "copybooks": $COPYBOOKS_JSON,
  "copybooksMetadata": {},
  "datasets": [],
  "call_flow": {
    "callees": [],
    "callers": []
  },
  "children": [
    {
      "nodeType": "CompilationUnitContext",
      "text": "Fallback AST - smojol-cli.jar not available",
      "copybooks": $COPYBOOKS_JSON,
      "children": []
    }
  ]
}
EOF
        ((AST_COUNT++))
        FILE_METRICS+=("$cbl_name|$file_loc|$file_size|$FILE_DURATION_MS|✓")
    done
fi

echo "  Generated: $AST_COUNT aggregated AST files"
echo ""

# Calculate timing BEFORE metrics generation
AST_END_TIME=$(date +%s%N)
AST_DURATION_SEC=$(( (AST_END_TIME - AST_START_TIME) / 1000000000 ))
AST_MINUTES=$((AST_DURATION_SEC / 60))
AST_SECONDS=$((AST_DURATION_SEC % 60))

# ============================================================================
# GENERATE PERFORMANCE METRICS REPORT (OPTIONAL)
# ============================================================================

METRICS_FILE="$OUTPUT_DIR/performance-metrics.txt"
METRICS_JSON="$OUTPUT_DIR/performance-metrics.json"

if [[ "$GENERATE_METRICS" == "true" ]]; then
    # Generate text metrics file
    {
        echo "COBOL AST Generation - Performance Metrics Report"
        echo "=================================================="
        echo ""
        echo "Per-File Metrics:"
        echo ""
        echo "  File Name              LOC    Size      Time(ms)  Status"
        echo "  ────────────────────────────────────────────────────────"
        
        for metric in "${FILE_METRICS[@]}"; do
            IFS='|' read -r fname loc size time status <<< "$metric"
            printf "  %-24s %5d  %7d   %8d   %s\n" "$fname" "$loc" "$size" "$time" "$status"
        done
        
        echo ""
        echo "Summary Metrics:"
        echo "  • Total files processed:  $AST_COUNT"
        echo "  • Total LOC:              $TOTAL_LOC"
        echo "  • Total execution time:   ${AST_MINUTES}m ${AST_SECONDS}s ($AST_DURATION_SEC seconds)"
        
        if [[ $TOTAL_LOC -gt 0 ]]; then
            AVG_TIME_PER_LOC=$((AST_DURATION_SEC * 1000 / TOTAL_LOC))
            PROCESSING_RATE=$((TOTAL_LOC / (AST_DURATION_SEC + 1)))
            echo "  • Time per 1000 LOC:      ~${AVG_TIME_PER_LOC}ms"
            echo "  • Processing rate:        ~${PROCESSING_RATE} LOC/sec"
        fi
        echo ""
        echo "Generated at: $(date)"
    } > "$METRICS_FILE"

    # Generate metrics file only if enabled

    {
        echo "{"
        echo '  "metrics": ['
        
        FIRST=true
        for metric in "${FILE_METRICS[@]}"; do
            IFS='|' read -r fname loc size time status <<< "$metric"
            
            if [[ "$FIRST" == "true" ]]; then
                FIRST=false
            else
                echo ","
            fi
            
            printf '    {"file": "%s", "loc": %d, "bytes": %d, "time_ms": %d, "status": "%s"}' \
                   "$fname" "$loc" "$size" "$time" "$status"
        done
        
        echo ""
        echo "  ],"
        echo "  \"summary\": {"
        echo "    \"total_files\": $AST_COUNT,"
        echo "    \"total_loc\": $TOTAL_LOC,"
        echo "    \"total_time_seconds\": $AST_DURATION_SEC,"
        
        if [[ $TOTAL_LOC -gt 0 ]]; then
            AVG_TIME_PER_LOC=$((AST_DURATION_SEC * 1000 / TOTAL_LOC))
            PROCESSING_RATE=$((TOTAL_LOC / (AST_DURATION_SEC + 1)))
            echo "    \"avg_time_per_1000_loc_ms\": $AVG_TIME_PER_LOC,"
            echo "    \"processing_rate_loc_per_sec\": $PROCESSING_RATE,"
        fi
        
        echo "    \"timestamp\": \"$(date)\""
        echo "  }"
        echo "}"
    } > "$METRICS_JSON"
fi

echo ""

# ============================================================================
# STEP 4-5: GENERATE DEPENDENCY GRAPHS (JSON + SVG) - OPTIONAL
# ============================================================================

if [[ "$GENERATE_GRAPHS" == "true" ]]; then
    echo -e "${BLUE}[Step 4-5] Generating dependency graphs${NC}"

    # Find Python interpreter
    PYTHON_CMD=""
    for cmd in python python3 python2; do
        if command -v "$cmd" &>/dev/null 2>&1 && "$cmd" --version &>/dev/null 2>&1; then
            PYTHON_CMD="$cmd"
            break
        fi
    done

    if [[ -z "$PYTHON_CMD" ]]; then
        echo "  Warning: Python not found, skipping graph generation"
    else
        # Generate global dependency graph (JSON format)
        GRAPH_FILE="$OUTPUT_DIR/dependency-graph.json"
        GRAPH_SCRIPT="$SCRIPT_DIR/generate_dependency_graph_json.py"
        
        if [[ -f "$GRAPH_SCRIPT" ]]; then
            if "$PYTHON_CMD" "$GRAPH_SCRIPT" \
                --cobol-dir "${CBL_DIRS[0]}" \
                --jcl-dir "${JCL_DIRS[0]:-}" \
                --ast-dir "$REPORT_DIR" \
                --output "$GRAPH_FILE" 2>/dev/null; then
                
                if [[ -f "$GRAPH_FILE" ]]; then
                    GRAPH_SIZE=$(du -h "$GRAPH_FILE" 2>/dev/null | awk '{print $1}')
                    echo "  Global dependency graph: $GRAPH_SIZE"
                fi
            fi
        fi
        
        # Generate individual program graphs (SVG format)
        PROGRAM_GRAPHS_DIR="$OUTPUT_DIR/program-graphs"
        mkdir -p "$PROGRAM_GRAPHS_DIR"
        
        SVG_SCRIPT="$SCRIPT_DIR/generate_graph_from_aggregated.py"
        if [[ -f "$SVG_SCRIPT" ]]; then
            AST_FILES=$(find "$REPORT_DIR" -name "*-aggregated.json" -type f 2>/dev/null)
            
            while IFS= read -r ast_file; do
                if [[ -z "$ast_file" ]]; then continue; fi
                
                PROGRAM_NAME=$(basename "$ast_file" "-aggregated.json")
                OUTPUT_SVG="$PROGRAM_GRAPHS_DIR/${PROGRAM_NAME}_dependencies.svg"
                
                "$PYTHON_CMD" "$SVG_SCRIPT" \
                    --input "$ast_file" \
                    --output "$OUTPUT_SVG" >/dev/null 2>&1
            done <<< "$AST_FILES"
            
            SVG_COUNT=$(find "$PROGRAM_GRAPHS_DIR" -name "*_dependencies.svg" -type f 2>/dev/null | wc -l)
            if [[ $SVG_COUNT -gt 0 ]]; then
                echo "  Generated: $SVG_COUNT individual program graphs"
            fi
        fi
    fi
else
    echo -e "${BLUE}[Step 4-5] Dependency graph generation disabled (use -g to enable)${NC}"
fi

echo ""

# ============================================================================
# STEP 5: GENERATE UI JSON FILES (jcl-analysis.json, copybook-analysis-complete.json)
# ============================================================================

echo -e "${BLUE}[Step 5/5] Generating UI JSON files${NC}"

# UI JSON script is now in the jcl parser folder
UI_JSON_SCRIPT="$PROJECT_ROOT/smojol-jcl/python/generate_ui_json.py"

# Check for virtual environment with jcl-parser installed
UI_PYTHON=""
if [[ -f "$PROJECT_ROOT/.venv/bin/python" ]]; then
    UI_PYTHON="$PROJECT_ROOT/.venv/bin/python"
elif [[ -f "$PROJECT_ROOT/.venv/Scripts/python.exe" ]]; then
    UI_PYTHON="$PROJECT_ROOT/.venv/Scripts/python.exe"
fi

if [[ -z "$UI_PYTHON" ]]; then
    # Try system Python
    for cmd in python python3; do
        if command -v "$cmd" &>/dev/null 2>&1 && "$cmd" --version &>/dev/null 2>&1; then
            UI_PYTHON="$cmd"
            break
        fi
    done
fi

if [[ -n "$UI_PYTHON" ]] && [[ -f "$UI_JSON_SCRIPT" ]]; then
    # UI script expects a single JCL dir; use aggregated temp dir if multiple
    UI_JCL_DIR="${JCL_DIRS[0]:-}"
    TEMP_UI_JCL=""
    if [[ ${#JCL_DIRS[@]} -gt 1 ]]; then
        TEMP_UI_JCL=$(mktemp -d)
        for jcl_d in "${JCL_DIRS[@]}"; do
            find "$jcl_d" -iname "*.jcl" -type f -exec cp {} "$TEMP_UI_JCL/" \; 2>/dev/null
        done
        UI_JCL_DIR="$TEMP_UI_JCL"
    fi
    
    "$UI_PYTHON" "$UI_JSON_SCRIPT" \
        -j "$UI_JCL_DIR" \
        -r "$REPORT_DIR" \
        -o "$OUTPUT_DIR" 2>&1 | while read line; do echo "  $line"; done
    
    [[ -n "$TEMP_UI_JCL" && -d "$TEMP_UI_JCL" ]] && rm -rf "$TEMP_UI_JCL"
    
    if [[ -f "$OUTPUT_DIR/jcl-analysis.json" ]] && [[ -f "$OUTPUT_DIR/copybook-analysis-complete.json" ]]; then
        echo -e "  ${GREEN}✓ UI JSON files generated${NC}"
    else
        echo -e "  ${YELLOW}⚠ Some UI JSON files may be missing${NC}"
    fi
else
    echo -e "  ${YELLOW}Warning: Cannot generate UI JSON files (Python or script not found)${NC}"
fi

echo ""

# ============================================================================
# SUMMARY
# ============================================================================

echo -e "${BLUE}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║            Analysis Complete                         ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════╝${NC}"
echo ""

echo "Analysis Summary:"
echo "  Mode:              $MODE"
echo "  CBL directories:   ${CBL_DIRS[*]}"
echo "  JCL directories:   ${JCL_DIRS[*]:-<none>}"
echo "  CPY directories:   ${CPY_DIRS[*]:-<none>}"
echo "  Files analyzed:    $CBL_COUNT COBOL, $JCL_COUNT JCL, $CPY_COUNT Copybooks"
echo "  Programs mapped:   $MAPPED of $CBL_COUNT"
echo "  ASTs generated:    $AST_COUNT"
echo "  Total LOC:         $TOTAL_LOC"
echo "  Execution time:    ${AST_MINUTES}m ${AST_SECONDS}s"
echo ""

# Display per-file metrics only if enabled
if [[ "$GENERATE_METRICS" == "true" ]]; then
    echo "Per-File Performance Metrics:"
    echo ""
    echo "  File Name              LOC    Size      Time(ms)  Status"
    echo "  ────────────────────────────────────────────────────────"

    for metric in "${FILE_METRICS[@]}"; do
        IFS='|' read -r fname loc size time status <<< "$metric"
        printf "  %-24s %5d  %7d   %8d   %s\n" "$fname" "$loc" "$size" "$time" "$status"
    done

    echo ""
    if [[ $TOTAL_LOC -gt 0 ]]; then
        echo "  Scalability Metrics:"
        echo "    • Total LOC processed:  $TOTAL_LOC"
        echo "    • Time per 1000 LOC:    ~$((AST_DURATION_SEC * 1000 / TOTAL_LOC))ms"
        echo "    • Processing rate:      ~$((TOTAL_LOC / (AST_DURATION_SEC + 1))) LOC/sec"
        echo ""
    fi
fi

echo "Output directories:"
echo "  📊 Mappings:      $MAPPINGS_FILE"
echo "  📁 ASTs:          $REPORT_DIR"
if [[ "$GENERATE_METRICS" == "true" ]]; then
    echo "  📈 Metrics:       $METRICS_FILE"
    echo "  📋 Metrics JSON:  $METRICS_JSON"
fi
if [[ "$GENERATE_GRAPHS" == "true" ]]; then
    echo "  🔗 Graphs:        $PROGRAM_GRAPHS_DIR"
fi
if [[ -f "$OUTPUT_DIR/jcl-analysis.json" ]]; then
    echo "  🖥️  UI JSON:       $OUTPUT_DIR/jcl-analysis.json"
    echo "                    $OUTPUT_DIR/copybook-analysis-complete.json"
fi
echo ""
echo ""

echo "Next steps:"
echo ""
echo "  📊 Review Generated Data:"
echo "     cat $MAPPINGS_FILE | jq ."
echo "     ls -la $REPORT_DIR"
if [[ "$GENERATE_METRICS" == "true" ]]; then
    echo ""
    echo "  📈 View Performance Metrics:"
    echo "     cat $METRICS_FILE"
    echo "     cat $METRICS_JSON | jq ."
fi
echo ""
echo "  🚀 Start/Restart the API (Required):"
echo "     cd $PROJECT_ROOT/smojol-rest-api"
echo "     # If API is already running, stop it first (Ctrl+C)"
echo "     java -Dast.base.path=../out -jar target/smojol-rest-api-1.0.0.jar"
echo ""
echo "  🌐 Start the Web UI (in another terminal):"
echo "     cd $PROJECT_ROOT/smojol-ui"
echo "     python -m http.server 3000"
echo ""
echo "  👉 Open in browser:"
echo "     http://localhost:3000"
echo ""
