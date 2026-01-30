"""
MCP server for COBOL search via COBOL Smojol API.

This server exposes tools to search and analyze COBOL programs through chat interfaces.
"""

import os
import httpx
from typing import Dict, List, Optional, Any
from mcp.server.fastmcp import FastMCP

# Configuration
COBOL_REST_URL = os.getenv("COBOL_REST_URL", "http://localhost:8080")

# Initialize FastMCP server
mcp = FastMCP("COBOL Search")


def get_cobol_client() -> httpx.Client:
    """Create an HTTP client for cobol-rest API."""
    return httpx.Client(base_url=COBOL_REST_URL, timeout=30.0)


def safe_api_call(endpoint: str) -> Optional[Dict[str, Any]]:
    """
    Make a safe API call with error handling.
    
    Args:
        endpoint: API endpoint path (e.g., "/api/programs/CBIMPORT")
    
    Returns:
        JSON response as dict or None if error
    """
    try:
        with get_cobol_client() as client:
            response = client.get(endpoint)
            response.raise_for_status()
            return response.json()
    except httpx.HTTPStatusError as e:
        if e.response.status_code == 404:
            return None
        return {"error": f"HTTP {e.response.status_code}: {e.response.text[:200]}"}
    except Exception as e:
        return {"error": f"Connection error: {str(e)}"}


# ============================================================================
# COBOL Search Tools
# ============================================================================

@mcp.tool()
def search_program(name: str, include_details: bool = True) -> Dict[str, Any]:
    """
    Recherche un programme COBOL par son nom.
    
    Args:
        name: Nom du programme COBOL (ex: "CBIMPORT")
        include_details: Inclure copybooks et dépendances (défaut: True)
    
    Returns:
        Informations complètes sur le programme ou message d'erreur
    """
    result = safe_api_call(f"/api/programs/{name}")
    
    if result is None:
        return {"error": f"Programme '{name}' non trouvé"}
    
    if "error" in result:
        return result
    
    # Format response
    formatted = {
        "name": result.get("name"),
        "path": result.get("path"),
        "copybooks_count": len(result.get("copybooks", [])),
        "callers_count": len(result.get("callers", [])),
        "callees_count": len(result.get("callees", []))
    }
    
    if include_details:
        formatted["copybooks"] = result.get("copybooks", [])
        formatted["callers"] = result.get("callers", [])
        formatted["callees"] = result.get("callees", [])
        formatted["jcls"] = result.get("jcls", [])
    
    return formatted


@mcp.tool()
def list_programs(filter_name: Optional[str] = None, limit: int = 50) -> Dict[str, Any]:
    """
    Liste tous les programmes COBOL disponibles.
    
    Args:
        filter_name: Filtre optionnel sur le nom (recherche partielle, ex: "CB")
        limit: Nombre maximum de résultats (défaut: 50)
    
    Returns:
        Liste des programmes avec statistiques
    """
    result = safe_api_call("/api/programs")
    
    if result is None or "error" in result:
        return result or {"error": "Failed to retrieve programs"}
    
    programs = result if isinstance(result, list) else []
    
    # Apply filter
    if filter_name:
        programs = [p for p in programs 
                   if filter_name.upper() in p.get("name", "").upper()]
    
    # Limit results
    programs = programs[:limit]
    
    return {
        "total_found": len(programs),
        "limit": limit,
        "programs": [
            {
                "name": p.get("name"),
                "copybooks_count": len(p.get("copybooks", [])),
                "has_callers": len(p.get("callers", [])) > 0,
                "has_callees": len(p.get("callees", [])) > 0
            }
            for p in programs
        ]
    }


@mcp.tool()
def find_copybook_usage(copybook_name: str) -> Dict[str, Any]:
    """
    Trouve tous les programmes utilisant un copybook spécifique.
    
    Args:
        copybook_name: Nom du copybook (ex: "CVACT01Y")
    
    Returns:
        Liste des programmes utilisant ce copybook
    """
    result = safe_api_call(f"/api/copybooks/{copybook_name}")
    
    if result is None:
        return {"error": f"Copybook '{copybook_name}' non trouvé"}
    
    if "error" in result:
        return result
    
    used_by = result.get("usedBy", [])
    
    return {
        "copybook": copybook_name,
        "used_by_count": len(used_by),
        "programs": used_by,
        "impact_level": "high" if len(used_by) > 5 else "medium" if len(used_by) > 2 else "low"
    }


@mcp.tool()
def analyze_dependencies(program_name: str) -> Dict[str, Any]:
    """
    Analyse les dépendances complètes d'un programme.
    
    Args:
        program_name: Nom du programme à analyser
    
    Returns:
        Graphe de dépendances (copybooks, appels, JCL)
    """
    result = safe_api_call(f"/api/programs/{program_name}")
    
    if result is None:
        return {"error": f"Programme '{program_name}' non trouvé"}
    
    if "error" in result:
        return result
    
    return {
        "program": program_name,
        "dependencies": {
            "copybooks": result.get("copybooks", []),
            "calls": result.get("callees", []),
            "called_by": result.get("callers", []),
            "jcls": result.get("jcls", [])
        },
        "complexity_score": (
            len(result.get("copybooks", [])) * 2 +
            len(result.get("callees", [])) * 3 +
            len(result.get("callers", [])) * 1
        )
    }


@mcp.tool()
def search_jcl(jcl_name: str) -> Dict[str, Any]:
    """
    Recherche un fichier JCL et ses programmes associés.
    
    Args:
        jcl_name: Nom du JCL
    
    Returns:
        Informations sur le JCL et ses programmes
    """
    result = safe_api_call(f"/api/jcls/{jcl_name}")
    
    if result is None:
        return {"error": f"JCL '{jcl_name}' non trouvé"}
    
    if "error" in result:
        return result
    
    return {
        "name": result.get("name"),
        "job_name": result.get("jobName", "N/A"),
        "programs": result.get("programs", []),
        "datasets": result.get("datasets", []),
        "programs_count": len(result.get("programs", []))
    }


@mcp.tool()
def search_copybook(copybook_name: str, include_details: bool = True) -> Dict[str, Any]:
    """
    Recherche un copybook par son nom avec détails complets.
    
    Args:
        copybook_name: Nom du copybook (ex: "CVACT01Y")
        include_details: Inclure la liste des programmes utilisant ce copybook
    
    Returns:
        Informations complètes sur le copybook
    """
    result = safe_api_call(f"/api/copybooks/{copybook_name}")
    
    if result is None:
        return {"error": f"Copybook '{copybook_name}' non trouvé"}
    
    if "error" in result:
        return result
    
    used_by = result.get("usedBy", [])
    
    formatted = {
        "name": result.get("name"),
        "used_by_count": len(used_by),
        "impact_level": "high" if len(used_by) > 5 else "medium" if len(used_by) > 2 else "low"
    }
    
    if include_details:
        formatted["used_by_programs"] = used_by
    
    return formatted


@mcp.tool()
def search_dataset(dataset_name: str, include_details: bool = True) -> Dict[str, Any]:
    """
    Recherche un dataset et ses usages.
    
    Args:
        dataset_name: Nom du dataset (ex: "CUSTOUT")
        include_details: Inclure les listes de programmes et JCL
    
    Returns:
        Informations complètes sur le dataset
    """
    result = safe_api_call(f"/api/datasets/{dataset_name}")
    
    if result is None:
        return {"error": f"Dataset '{dataset_name}' non trouvé"}
    
    if "error" in result:
        return result
    
    used_by_cobol = result.get("usedByCobol", [])
    used_by_jcls = result.get("usedByJcls", [])
    
    formatted = {
        "name": result.get("name"),
        "organization": result.get("organization", "N/A"),
        "used_by_programs_count": len(used_by_cobol),
        "used_by_jcls_count": len(used_by_jcls),
        "total_references": len(used_by_cobol) + len(used_by_jcls)
    }
    
    if include_details:
        formatted["used_by_programs"] = used_by_cobol
        formatted["used_by_jcls"] = used_by_jcls
    
    return formatted


@mcp.tool()
def list_copybooks(filter_name: Optional[str] = None, limit: int = 50) -> Dict[str, Any]:
    """
    Liste tous les copybooks disponibles.
    
    Args:
        filter_name: Filtre optionnel sur le nom (recherche partielle, ex: "CVACT")
        limit: Nombre maximum de résultats (défaut: 50)
    
    Returns:
        Liste des copybooks avec statistiques d'utilisation
    """
    result = safe_api_call("/api/copybooks")
    
    if result is None or "error" in result:
        return result or {"error": "Failed to retrieve copybooks"}
    
    copybooks = result if isinstance(result, list) else []
    
    # Apply filter
    if filter_name:
        copybooks = [c for c in copybooks 
                    if filter_name.upper() in c.get("name", "").upper()]
    
    # Limit results
    copybooks = copybooks[:limit]
    
    return {
        "total_found": len(copybooks),
        "limit": limit,
        "copybooks": [
            {
                "name": c.get("name"),
                "used_by_count": len(c.get("usedBy", [])),
                "impact_level": "high" if len(c.get("usedBy", [])) > 5 else "medium" if len(c.get("usedBy", [])) > 2 else "low"
            }
            for c in copybooks
        ]
    }


@mcp.tool()
def list_jcls(filter_name: Optional[str] = None, limit: int = 50) -> Dict[str, Any]:
    """
    Liste tous les fichiers JCL disponibles.
    
    Args:
        filter_name: Filtre optionnel sur le nom (recherche partielle)
        limit: Nombre maximum de résultats (défaut: 50)
    
    Returns:
        Liste des JCL avec statistiques
    """
    result = safe_api_call("/api/jcls")
    
    if result is None or "error" in result:
        return result or {"error": "Failed to retrieve JCLs"}
    
    jcls = result if isinstance(result, list) else []
    
    # Apply filter
    if filter_name:
        jcls = [j for j in jcls 
               if filter_name.upper() in j.get("name", "").upper()]
    
    # Limit results
    jcls = jcls[:limit]
    
    return {
        "total_found": len(jcls),
        "limit": limit,
        "jcls": [
            {
                "name": j.get("name"),
                "job_name": j.get("jobName", "N/A"),
                "programs_count": len(j.get("programs", [])),
                "datasets_count": len(j.get("datasets", []))
            }
            for j in jcls
        ]
    }


@mcp.tool()
def list_datasets(filter_name: Optional[str] = None, limit: int = 50) -> Dict[str, Any]:
    """
    Liste tous les datasets disponibles.
    
    Args:
        filter_name: Filtre optionnel sur le nom (recherche partielle, ex: "CUST")
        limit: Nombre maximum de résultats (défaut: 50)
    
    Returns:
        Liste des datasets avec statistiques d'utilisation
    """
    result = safe_api_call("/api/datasets")
    
    if result is None or "error" in result:
        return result or {"error": "Failed to retrieve datasets"}
    
    datasets = result if isinstance(result, list) else []
    
    # Apply filter
    if filter_name:
        datasets = [d for d in datasets 
                   if filter_name.upper() in d.get("name", "").upper()]
    
    # Limit results
    datasets = datasets[:limit]
    
    return {
        "total_found": len(datasets),
        "limit": limit,
        "datasets": [
            {
                "name": d.get("name"),
                "organization": d.get("organization", "N/A"),
                "used_by_programs": len(d.get("usedByCobol", [])),
                "used_by_jcls": len(d.get("usedByJcls", []))
            }
            for d in datasets
        ]
    }


@mcp.tool()
def find_programs_using_dataset(dataset_name: str) -> Dict[str, Any]:
    """
    Trouve tous les programmes COBOL qui accèdent à un dataset.
    
    Args:
        dataset_name: Nom du dataset (ex: "CUSTOUT")
    
    Returns:
        Liste des programmes accédant ce dataset
    """
    result = safe_api_call(f"/api/datasets/{dataset_name}")
    
    if result is None:
        return {"error": f"Dataset '{dataset_name}' non trouvé"}
    
    if "error" in result:
        return result
    
    programs = result.get("usedByCobol", [])
    
    return {
        "dataset": dataset_name,
        "programs_count": len(programs),
        "programs": programs,
        "impact_level": "high" if len(programs) > 5 else "medium" if len(programs) > 2 else "low"
    }


# ============================================================================
# Analysis & Impact Tools
# ============================================================================

@mcp.tool()
def analyze_impact(
    entity_name: str, 
    entity_type: str = "auto",
    include_transitive: bool = False
) -> Dict[str, Any]:
    """
    Analyse l'impact de modification d'un élément (programme, copybook, dataset, JCL).
    
    Args:
        entity_name: Nom de l'entité
        entity_type: Type (program, copybook, dataset, jcl, auto)
        include_transitive: Inclure les dépendances transitives (niveau 2)
    
    Returns:
        Analyse d'impact complète avec tous les éléments affectés
    """
    impact = {
        "entity": entity_name,
        "type": entity_type,
        "affected_programs": [],
        "affected_jcls": [],
        "affected_copybooks": [],
        "risk_level": "low",
        "recommendations": []
    }
    
    # Auto-detect entity type
    if entity_type == "auto":
        if safe_api_call(f"/api/programs/{entity_name}"):
            entity_type = "program"
        elif safe_api_call(f"/api/copybooks/{entity_name}"):
            entity_type = "copybook"
        elif safe_api_call(f"/api/datasets/{entity_name}"):
            entity_type = "dataset"
        elif safe_api_call(f"/api/jcls/{entity_name}"):
            entity_type = "jcl"
        else:
            return {"error": f"Entity '{entity_name}' not found"}
    
    impact["type"] = entity_type
    
    # Analyze based on type
    if entity_type == "copybook":
        copybook = safe_api_call(f"/api/copybooks/{entity_name}")
        if copybook and "error" not in copybook:
            programs = copybook.get("usedBy", [])
            impact["affected_programs"] = programs
            impact["risk_level"] = "critical" if len(programs) > 10 else "high" if len(programs) > 5 else "medium"
            impact["recommendations"] = [
                f"Recompiler {len(programs)} programmes",
                "Tests de régression nécessaires",
                "Impact élevé - planifier soigneusement"
            ] if len(programs) > 5 else ["Impact modéré"]
            
            # Transitive: find JCLs using these programs
            if include_transitive:
                jcls_set = set()
                for prog in programs[:20]:  # Limit for performance
                    prog_data = safe_api_call(f"/api/programs/{prog}")
                    if prog_data and "error" not in prog_data:
                        jcls_set.update(prog_data.get("jcls", []))
                impact["affected_jcls"] = list(jcls_set)
    
    elif entity_type == "program":
        program = safe_api_call(f"/api/programs/{entity_name}")
        if program and "error" not in program:
            callers = program.get("callers", [])
            jcls = program.get("jcls", [])
            impact["affected_programs"] = callers
            impact["affected_jcls"] = jcls
            total_impact = len(callers) + len(jcls)
            impact["risk_level"] = "high" if total_impact > 5 else "medium" if total_impact > 2 else "low"
            
            # Transitive: find callers of callers
            if include_transitive and callers:
                indirect_callers = set()
                for caller in callers[:10]:
                    caller_data = safe_api_call(f"/api/programs/{caller}")
                    if caller_data and "error" not in caller_data:
                        indirect_callers.update(caller_data.get("callers", []))
                impact["indirect_callers"] = list(indirect_callers)
    
    elif entity_type == "dataset":
        dataset = safe_api_call(f"/api/datasets/{entity_name}")
        if dataset and "error" not in dataset:
            programs = dataset.get("usedByCobol", [])
            jcls = dataset.get("usedByJcls", [])
            impact["affected_programs"] = programs
            impact["affected_jcls"] = jcls
            total = len(programs) + len(jcls)
            impact["risk_level"] = "critical" if total > 15 else "high" if total > 8 else "medium"
            impact["recommendations"] = [
                "Modifier le format du dataset impactera tous ces programmes",
                "Tests d'intégration nécessaires",
                "Vérifier les contraintes de données"
            ] if total > 8 else ["Impact modéré sur le dataset"]
    
    return impact


@mcp.tool()
def get_statistics() -> Dict[str, Any]:
    """
    Retourne les statistiques globales du système COBOL.
    
    Returns:
        Statistiques complètes (totaux, top programmes, etc.)
    """
    stats = {
        "totals": {},
        "top_programs": [],
        "top_copybooks": [],
        "complexity_analysis": {}
    }
    
    # Get all data
    programs = safe_api_call("/api/programs") or []
    copybooks = safe_api_call("/api/copybooks") or []
    jcls = safe_api_call("/api/jcls") or []
    datasets = safe_api_call("/api/datasets") or []
    
    # Calculate totals
    stats["totals"] = {
        "programs": len(programs),
        "copybooks": len(copybooks),
        "jcls": len(jcls),
        "datasets": len(datasets)
    }
    
    # Find most complex programs
    if isinstance(programs, list):
        program_complexity = []
        for p in programs:
            complexity = (
                len(p.get("copybooks", [])) * 2 +
                len(p.get("callees", [])) * 3 +
                len(p.get("callers", [])) * 1
            )
            program_complexity.append({
                "name": p.get("name"),
                "complexity_score": complexity,
                "copybooks": len(p.get("copybooks", [])),
                "calls": len(p.get("callees", []))
            })
        
        program_complexity.sort(key=lambda x: x["complexity_score"], reverse=True)
        stats["top_programs"] = program_complexity[:10]
    
    # Find most used copybooks
    if isinstance(copybooks, list):
        copybook_usage = [
            {
                "name": c.get("name"),
                "used_by_count": len(c.get("usedBy", []))
            }
            for c in copybooks
        ]
        copybook_usage.sort(key=lambda x: x["used_by_count"], reverse=True)
        stats["top_copybooks"] = copybook_usage[:10]
    
    # Complexity analysis
    if isinstance(programs, list):
        high_complexity = sum(1 for p in program_complexity if p["complexity_score"] > 30)
        medium_complexity = sum(1 for p in program_complexity if 15 < p["complexity_score"] <= 30)
        low_complexity = sum(1 for p in program_complexity if p["complexity_score"] <= 15)
        
        stats["complexity_analysis"] = {
            "high_complexity_count": high_complexity,
            "medium_complexity_count": medium_complexity,
            "low_complexity_count": low_complexity
        }
    
    return stats


@mcp.tool()
def find_unused_copybooks() -> Dict[str, Any]:
    """
    Trouve les copybooks qui ne sont utilisés par aucun programme.
    
    Returns:
        Liste des copybooks potentiellement inutilisés
    """
    copybooks = safe_api_call("/api/copybooks")
    
    if not isinstance(copybooks, list):
        return {"error": "Failed to retrieve copybooks"}
    
    unused = [
        c.get("name")
        for c in copybooks
        if len(c.get("usedBy", [])) == 0
    ]
    
    return {
        "unused_count": len(unused),
        "total_copybooks": len(copybooks),
        "percentage": round(len(unused) / len(copybooks) * 100, 1) if copybooks else 0,
        "unused_copybooks": unused,
        "recommendation": "Ces copybooks peuvent potentiellement être supprimés après vérification"
    }


@mcp.tool()
def find_orphan_programs() -> Dict[str, Any]:
    """
    Trouve les programmes jamais appelés et non référencés dans les JCL.
    
    Returns:
        Liste des programmes potentiellement orphelins
    """
    programs = safe_api_call("/api/programs")
    
    if not isinstance(programs, list):
        return {"error": "Failed to retrieve programs"}
    
    orphans = []
    for p in programs:
        has_callers = len(p.get("callers", [])) > 0
        has_jcls = len(p.get("jcls", [])) > 0
        
        if not has_callers and not has_jcls:
            orphans.append({
                "name": p.get("name"),
                "copybooks_count": len(p.get("copybooks", [])),
                "calls_others": len(p.get("callees", [])) > 0
            })
    
    return {
        "orphan_count": len(orphans),
        "total_programs": len(programs),
        "percentage": round(len(orphans) / len(programs) * 100, 1) if programs else 0,
        "orphan_programs": orphans,
        "recommendation": "Ces programmes ne sont jamais appelés - vérifier s'ils sont obsolètes"
    }


@mcp.tool()
def analyze_call_chain(program_name: str, max_depth: int = 5) -> Dict[str, Any]:
    """
    Analyse récursive de la chaîne d'appels d'un programme.
    
    Args:
        program_name: Nom du programme racine
        max_depth: Profondeur maximale de récursion (défaut: 5)
    
    Returns:
        Arbre complet des appels avec tous les niveaux
    """
    def build_call_tree(prog_name: str, depth: int, visited: set) -> Dict:
        if depth > max_depth or prog_name in visited:
            return {"name": prog_name, "circular": prog_name in visited, "children": []}
        
        visited.add(prog_name)
        prog_data = safe_api_call(f"/api/programs/{prog_name}")
        
        if not prog_data or "error" in prog_data:
            # Gracefully handle external/missing programs (e.g., CEE3ABD, COBDATFT)
            return {"name": prog_name, "external": True, "children": []}
        
        callees = prog_data.get("callees", [])
        children = [
            build_call_tree(callee, depth + 1, visited.copy())
            for callee in callees
        ]
        
        return {
            "name": prog_name,
            "depth": depth,
            "calls_count": len(callees),
            "external": False,
            "children": children
        }
    
    tree = build_call_tree(program_name, 0, set())
    
    # Calculate statistics
    def count_nodes(node):
        return 1 + sum(count_nodes(child) for child in node.get("children", []))
    
    return {
        "root_program": program_name,
        "max_depth_analyzed": max_depth,
        "total_programs_in_chain": count_nodes(tree),
        "call_tree": tree
    }


@mcp.tool()
def find_complex_programs(top_n: int = 10, metric: str = "all") -> Dict[str, Any]:
    """
    Trouve les programmes les plus complexes selon différentes métriques.
    
    Args:
        top_n: Nombre de programmes à retourner (défaut: 10)
        metric: Métrique de complexité (dependencies, copybooks, calls, all)
    
    Returns:
        Liste des programmes les plus complexes triés par score
    """
    programs = safe_api_call("/api/programs")
    
    if not isinstance(programs, list):
        return {"error": "Failed to retrieve programs"}
    
    scored_programs = []
    
    for p in programs:
        copybook_count = len(p.get("copybooks", []))
        callee_count = len(p.get("callees", []))
        caller_count = len(p.get("callers", []))
        
        if metric == "copybooks":
            score = copybook_count
        elif metric == "calls":
            score = callee_count + caller_count
        elif metric == "dependencies":
            score = copybook_count + callee_count
        else:  # "all"
            score = copybook_count * 2 + callee_count * 3 + caller_count * 1
        
        scored_programs.append({
            "name": p.get("name"),
            "complexity_score": score,
            "copybooks": copybook_count,
            "calls_out": callee_count,
            "called_by": caller_count,
            "total_dependencies": copybook_count + callee_count + caller_count
        })
    
    scored_programs.sort(key=lambda x: x["complexity_score"], reverse=True)
    
    return {
        "metric_used": metric,
        "top_count": min(top_n, len(scored_programs)),
        "complex_programs": scored_programs[:top_n],
        "recommendation": "Prioriser ces programmes pour refactoring ou documentation approfondie"
    }


@mcp.tool()
def search_by_pattern(
    pattern: str, 
    entity_type: str = "all",
    case_sensitive: bool = False
) -> Dict[str, Any]:
    """
    Recherche par pattern (wildcard) dans tous les éléments.
    
    Args:
        pattern: Pattern de recherche (supporte * comme wildcard)
        entity_type: Type d'entités (program, copybook, jcl, dataset, all)
        case_sensitive: Respecter la casse (défaut: False)
    
    Returns:
        Résultats de recherche pour tous les types demandés
    """
    import re
    
    # Convert wildcard pattern to regex
    regex_pattern = pattern.replace("*", ".*")
    if not case_sensitive:
        regex_pattern = f"(?i){regex_pattern}"
    
    regex = re.compile(regex_pattern)
    results = {}
    
    if entity_type in ["program", "all"]:
        programs = safe_api_call("/api/programs") or []
        if isinstance(programs, list):
            results["programs"] = [
                p.get("name") for p in programs
                if regex.search(p.get("name", ""))
            ]
    
    if entity_type in ["copybook", "all"]:
        copybooks = safe_api_call("/api/copybooks") or []
        if isinstance(copybooks, list):
            results["copybooks"] = [
                c.get("name") for c in copybooks
                if regex.search(c.get("name", ""))
            ]
    
    if entity_type in ["jcl", "all"]:
        jcls = safe_api_call("/api/jcls") or []
        if isinstance(jcls, list):
            results["jcls"] = [
                j.get("name") for j in jcls
                if regex.search(j.get("name", ""))
            ]
    
    if entity_type in ["dataset", "all"]:
        datasets = safe_api_call("/api/datasets") or []
        if isinstance(datasets, list):
            results["datasets"] = [
                d.get("name") for d in datasets
                if regex.search(d.get("name", ""))
            ]
    
    total = sum(len(v) for v in results.values())
    
    return {
        "pattern": pattern,
        "entity_type": entity_type,
        "total_matches": total,
        "results": results
    }


@mcp.tool()
def compare_programs(program1: str, program2: str) -> Dict[str, Any]:
    """
    Compare deux programmes COBOL (dépendances, complexité, similarité).
    
    Args:
        program1: Nom du premier programme
        program2: Nom du second programme
    
    Returns:
        Analyse comparative complète
    """
    prog1 = safe_api_call(f"/api/programs/{program1}")
    prog2 = safe_api_call(f"/api/programs/{program2}")
    
    if not prog1 or "error" in prog1:
        return {"error": f"Programme '{program1}' non trouvé"}
    if not prog2 or "error" in prog2:
        return {"error": f"Programme '{program2}' non trouvé"}
    
    # Extract data
    copybooks1 = set(prog1.get("copybooks", []))
    copybooks2 = set(prog2.get("copybooks", []))
    callees1 = set(prog1.get("callees", []))
    callees2 = set(prog2.get("callees", []))
    
    # Calculate similarity
    common_copybooks = copybooks1 & copybooks2
    common_callees = callees1 & callees2
    
    total_unique = len(copybooks1 | copybooks2) + len(callees1 | callees2)
    total_common = len(common_copybooks) + len(common_callees)
    similarity_score = (total_common / total_unique * 100) if total_unique > 0 else 0
    
    return {
        "program1": {
            "name": program1,
            "copybooks": len(copybooks1),
            "calls": len(callees1),
            "complexity": len(copybooks1) * 2 + len(callees1) * 3
        },
        "program2": {
            "name": program2,
            "copybooks": len(copybooks2),
            "calls": len(callees2),
            "complexity": len(copybooks2) * 2 + len(callees2) * 3
        },
        "comparison": {
            "common_copybooks": list(common_copybooks),
            "common_calls": list(common_callees),
            "similarity_percentage": round(similarity_score, 1),
            "unique_to_program1": {
                "copybooks": list(copybooks1 - copybooks2),
                "calls": list(callees1 - callees2)
            },
            "unique_to_program2": {
                "copybooks": list(copybooks2 - copybooks1),
                "calls": list(callees2 - callees1)
            }
        }
    }


# ============================================================================
# Resources
# ============================================================================

@mcp.resource("cobol://status")
def get_cobol_status() -> str:
    """Get the current status of the cobol-rest API connection."""
    try:
        with get_cobol_client() as client:
            response = client.get("/api/health")
            if response.status_code == 200:
                data = response.json()
                return f"✅ API Status: {data.get('status', 'OK')} - Connected to {COBOL_REST_URL}"
            return f"⚠️ API returned status {response.status_code}"
    except Exception as e:
        return f"❌ Error connecting to API at {COBOL_REST_URL}: {str(e)}"


# ============================================================================
# Main Entry Point
# ============================================================================

def main():
    """Run the MCP server."""
    mcp.run()


if __name__ == "__main__":
    main()
