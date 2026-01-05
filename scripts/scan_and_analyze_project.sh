#!/bin/bash

################################################################################
# scan_and_analyze_project.sh
# 
# Orchestrates complete project analysis pipeline:
# 1. Scans directory structure (CBL, JCL, CPY files)
# 2. Creates CBL <-> JCL mappings
# 3. Generates aggregated ASTs for all programs
# 4. Builds dependency graph from ASTs and JCL relationships
#
# Usage:
#   bash scripts/scan_and_analyze_project.sh <cobol_dir> <jcl_dir> <cpy_dir> [output_dir] [options]
#
# Options:
#   -g, --graph       Generate dependency graphs (JSON + SVG) - disabled by default
#
# Example:
#   bash scripts/scan_and_analyze_project.sh app/cbl app/jcl app/cpy ./out
#   bash scripts/scan_and_analyze_project.sh app/cbl app/jcl app/cpy ./out -g
################################################################################

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Arguments
COBOL_DIR="$1"
JCL_DIR="$2"
CPY_DIR="$3"
OUTPUT_DIR="${4:-./out}"

# Parse optional flags (disabled by default)
GENERATE_GRAPHS=false
for arg in "${@:5}"; do
    case "$arg" in
        -g|--graph)
            GENERATE_GRAPHS=true
            ;;
    esac
done

# Validate inputs
[[ -d "$COBOL_DIR" ]] || { echo -e "${RED}ERROR: COBOL directory not found: $COBOL_DIR${NC}"; exit 1; }
[[ -d "$JCL_DIR" ]] || { echo -e "${RED}ERROR: JCL directory not found: $JCL_DIR${NC}"; exit 1; }
[[ -d "$CPY_DIR" ]] || { echo -e "${RED}ERROR: Copybook directory not found: $CPY_DIR${NC}"; exit 1; }

mkdir -p "$OUTPUT_DIR"

echo -e "${BLUE}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     COBOL Project Analysis & AST Generation          ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════╝${NC}"
echo ""

# ============================================================================
# STEP 1: SCAN PROJECT STRUCTURE
# ============================================================================

echo -e "${BLUE}[Step 1/4] Scanning project structure${NC}"
echo "  Source:     $COBOL_DIR"
echo "  JCL:        $JCL_DIR"
echo "  Copybooks:  $CPY_DIR"
echo "  Output:     $OUTPUT_DIR"
echo ""

CBL_FILES=$(find "$COBOL_DIR" -name "*.cbl" -type f)
JCL_FILES=$(find "$JCL_DIR" -name "*.jcl" -type f)
CPY_FILES=$(find "$CPY_DIR" -name "*.cpy" -type f)

CBL_COUNT=$(echo "$CBL_FILES" | grep -v '^$' | wc -l)
JCL_COUNT=$(echo "$JCL_FILES" | grep -v '^$' | wc -l)
CPY_COUNT=$(echo "$CPY_FILES" | grep -v '^$' | wc -l)

echo "  Found: $CBL_COUNT CBL files, $JCL_COUNT JCL files, $CPY_COUNT copybooks"
echo ""

# ============================================================================
# STEP 2: CREATE CBL-JCL MAPPINGS (Using Python script for accurate matching)
# ============================================================================

echo -e "${BLUE}[Step 2/4] Creating CBL-JCL mappings${NC}"

MAPPINGS_FILE="$OUTPUT_DIR/project-analysis.json"
mkdir -p "$OUTPUT_DIR"

JCL_ANALYSIS_SCRIPT="$SCRIPT_DIR/analyze_jcl_to_cobol.py"

# Try to find Python
PYTHON_CMD=""
for cmd in python python3 python2; do
    if command -v "$cmd" &>/dev/null 2>&1; then
        if "$cmd" --version &>/dev/null 2>&1; then
            PYTHON_CMD="$cmd"
            break
        fi
    fi
done

if [[ -n "$PYTHON_CMD" ]] && [[ -f "$JCL_ANALYSIS_SCRIPT" ]]; then
    # Convert paths to Windows format for Python if on Windows/Git Bash
    PYTHON_JCL_DIR="$JCL_DIR"
    PYTHON_COBOL_DIR="$COBOL_DIR"
    
    if [[ "$JCL_DIR" =~ ^/c/ ]]; then
        PYTHON_JCL_DIR=$(echo "$JCL_DIR" | sed 's|^/c/|C:/|' | sed 's|/|\\|g')
    fi
    if [[ "$COBOL_DIR" =~ ^/c/ ]]; then
        PYTHON_COBOL_DIR=$(echo "$COBOL_DIR" | sed 's|^/c/|C:/|' | sed 's|/|\\|g')
    fi
    
    # Use Python script for accurate JCL-COBOL matching via PGM= extraction
    TEMP_OUTPUT=$(mktemp)
    "$PYTHON_CMD" "$JCL_ANALYSIS_SCRIPT" \
        -j "$PYTHON_JCL_DIR" \
        -c "$PYTHON_COBOL_DIR" \
        --json > "$TEMP_OUTPUT" 2>&1
    PYTHON_EXIT=$?
    
    if [[ $PYTHON_EXIT -eq 0 ]] && [[ -s "$TEMP_OUTPUT" ]]; then
        cp "$TEMP_OUTPUT" "$MAPPINGS_FILE"
        rm "$TEMP_OUTPUT"
        
        # Get count before any further processing
        MAPPED=$(grep -c '"program"' "$MAPPINGS_FILE" 2>/dev/null || echo "0")
        echo "  Matched: $MAPPED programs using PGM= extraction"
    else
        echo -e "${YELLOW}  Warning: Python script analysis failed${NC}"
        echo "{}" > "$MAPPINGS_FILE"
        MAPPED=0
    fi
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
    # Determine the correct copybook directory
    CPY_SEARCH_DIR="$CPY_DIR"
    TEMP_CPY_DIR=""
    
    # Count copybooks at top level
    cpy_count=$(find "$CPY_DIR" -maxdepth 1 -name "*.cpy" -type f 2>/dev/null | wc -l)
    
    # If no copybooks at top level, check for cpy subdirectory
    if [[ $cpy_count -eq 0 ]] && [[ -d "$CPY_DIR/cpy" ]]; then
        CPY_SEARCH_DIR="$CPY_DIR/cpy"
        cpy_count=$(find "$CPY_SEARCH_DIR" -maxdepth 1 -name "*.cpy" -type f 2>/dev/null | wc -l)
    fi
    
    # For modular structure: aggregate all copybooks to temp directory
    if [[ $cpy_count -eq 0 ]]; then
        total_cpy=$(find "$CPY_DIR" -name "*.cpy" -type f 2>/dev/null | wc -l)
        if [[ $total_cpy -gt 0 ]]; then
            TEMP_CPY_DIR=$(mktemp -d)
            find "$CPY_DIR" -name "*.cpy" -type f -exec cp {} "$TEMP_CPY_DIR/" \;
            CPY_SEARCH_DIR="$TEMP_CPY_DIR"
            cpy_count=$total_cpy
        fi
    fi
    
    # Use smojol-cli to generate proper ASTs with WRITE_AGGREGATED_JCL_AST
    for cbl_file in $CBL_FILES; do
        if [[ ! -f "$cbl_file" ]]; then continue; fi
        
        cbl_name=$(basename "$cbl_file" .cbl)
        cbl_name_upper=$(echo "$cbl_name" | tr '[:lower:]' '[:upper:]')
        
        # Count lines of code for this file
        file_loc=$(wc -l < "$cbl_file" 2>/dev/null || echo 0)
        ((TOTAL_LOC += file_loc))
        
        # Get JCL files for this program from Step 2 mappings
        PROGRAM_JCL_DIR="$JCL_DIR"
        TEMP_PROGRAM_JCL_DIR=""
        
        if [[ -n "${JCL_MAPPINGS[$cbl_name_upper]}" ]]; then
            TEMP_PROGRAM_JCL_DIR=$(mktemp -d)
            IFS='|' read -ra jcl_files <<< "${JCL_MAPPINGS[$cbl_name_upper]}"
            
            JCL_FOUND=0
            for jcl_file_path in "${jcl_files[@]}"; do
                full_jcl_path="$JCL_DIR/$jcl_file_path"
                
                if [[ ! -f "$full_jcl_path" ]]; then
                    jcl_basename=$(basename "$jcl_file_path")
                    full_jcl_path=$(find "$JCL_DIR" -iname "$jcl_basename" -type f 2>/dev/null | head -1)
                fi
                
                if [[ -f "$full_jcl_path" ]]; then
                    cp "$full_jcl_path" "$TEMP_PROGRAM_JCL_DIR/" 2>/dev/null || true
                    ((JCL_FOUND++))
                fi
            done
            
            if [[ $JCL_FOUND -gt 0 ]]; then
                PROGRAM_JCL_DIR="$TEMP_PROGRAM_JCL_DIR"
            fi
        fi
        
        # Generate aggregated AST with correct JCL context
        if java -jar "$JAR_PATH" run \
            -c WRITE_AGGREGATED_JCL_AST \
            -j "$PROGRAM_JCL_DIR" \
            -s "$COBOL_DIR" \
            -cp "$CPY_SEARCH_DIR" \
            -r "$REPORT_DIR" \
            "$cbl_name.cbl" >/dev/null 2>&1; then
            ((AST_COUNT++))
        fi
        
        # Clean up temporary JCL directory for this program
        if [[ -n "$TEMP_PROGRAM_JCL_DIR" && -d "$TEMP_PROGRAM_JCL_DIR" ]]; then
            rm -rf "$TEMP_PROGRAM_JCL_DIR"
        fi
    done
    
    # Clean up temporary copybook directory if created
    if [[ -n "$TEMP_CPY_DIR" && -d "$TEMP_CPY_DIR" ]]; then
        rm -rf "$TEMP_CPY_DIR"
    fi
else
    # Count lines of code even for fallback
    for cbl_file in $CBL_FILES; do
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
    
    for cbl_file in $CBL_FILES; do
        if [[ ! -f "$cbl_file" ]]; then continue; fi
        
        cbl_name=$(basename "$cbl_file" .cbl)
        ast_dir="$REPORT_DIR/$cbl_name.cbl.report/ast/aggregated"
        mkdir -p "$ast_dir"
        
        # Create minimal AST JSON with basic structure
        cat > "$ast_dir/$cbl_name-aggregated.json" <<'EOF'
{
  "nodeType": "StartRuleContext",
  "text": "COBOL program stub - full AST generation requires smojol-cli JAR",
  "copybooks": [],
  "children": [
    {
      "nodeType": "CompilationUnitContext",
      "text": "Fallback AST",
      "copybooks": [],
      "children": []
    }
  ]
}
EOF
        ((AST_COUNT++))
    done
fi

echo "  Generated: $AST_COUNT aggregated AST files"
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
                --cobol-dir "$COBOL_DIR" \
                --jcl-dir "$JCL_DIR" \
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
# SUMMARY
# ============================================================================

echo -e "${BLUE}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║            Analysis Complete                         ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════╝${NC}"
echo ""

# Calculate timing
AST_END_TIME=$(date +%s%N)
AST_DURATION_SEC=$(( (AST_END_TIME - AST_START_TIME) / 1000000000 ))
AST_MINUTES=$((AST_DURATION_SEC / 60))
AST_SECONDS=$((AST_DURATION_SEC % 60))

echo "Analysis Summary:"
echo "  Files analyzed:    $CBL_COUNT COBOL, $JCL_COUNT JCL, $CPY_COUNT Copybooks"
echo "  Programs mapped:   $MAPPED of $CBL_COUNT"
echo "  ASTs generated:    $AST_COUNT"
echo "  Execution time:    ${AST_MINUTES}m ${AST_SECONDS}s"
echo ""

echo "Output directories:"
echo "  📊 Mappings:    $MAPPINGS_FILE"
echo "  📁 ASTs:        $REPORT_DIR"
if [[ "$GENERATE_GRAPHS" == "true" ]]; then
    echo "  📈 Graphs:      $PROGRAM_GRAPHS_DIR"
fi
echo ""
echo ""

echo "Next steps:"
echo "  1. Review mappings: cat $MAPPINGS_FILE | jq ."
echo "  2. Check ASTs:      ls -la $REPORT_DIR"
echo "  3. View graph:      cat $GRAPH_FILE | jq ."
echo ""
