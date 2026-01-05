#!/usr/bin/env python3
"""
Génère un graphe de dépendances à partir du fichier JSON agrégé (AST COBOL).
Visualise les relations entre le programme COBOL, les fichiers et les copybooks.

Extrait automatiquement:
- Programme COBOL principal
- Fichiers (INPUT/OUTPUT/INDEXED)
- Copybooks utilisés
- Contexte JCL si disponible
"""

import json
import sys
import os
import re
from pathlib import Path

try:
    import graphviz
except ImportError:
    print("[ERROR] Module graphviz non installé.")
    print("   Installez-le avec: pip install graphviz")
    sys.exit(1)


def extract_files_from_cobol_text(text):
    """Extrait les fichiers SELECT du code COBOL"""
    files = {}
    
    # Pattern pour SELECT ... ASSIGN TO
    select_pattern = r'SELECT\s+(\S+)\s+ASSIGN\s+TO\s+(\S+)'
    org_pattern = r'ORGANIZATION\s+IS\s+(\S+)'
    access_pattern = r'ACCESS\s+MODE\s+IS\s+(\S+)'
    
    # Trouver tous les SELECT
    selects = re.finditer(select_pattern, text, re.IGNORECASE)
    
    for select_match in selects:
        file_name = select_match.group(1).upper()
        assign_to = select_match.group(2).upper()
        
        # Chercher les propriétés après SELECT
        select_end = select_match.end()
        next_select_start = text.find('SELECT', select_end)
        if next_select_start == -1:
            section_text = text[select_end:]
        else:
            section_text = text[select_end:next_select_start]
        
        # Organisation et Access Mode
        org_match = re.search(org_pattern, section_text, re.IGNORECASE)
        access_match = re.search(access_pattern, section_text, re.IGNORECASE)
        
        organization = org_match.group(1).upper() if org_match else "SEQUENTIAL"
        access_mode = access_match.group(1).upper() if access_match else "SEQUENTIAL"
        
        files[file_name] = {
            'assign_to': assign_to,
            'organization': organization,
            'access_mode': access_mode
        }
    
    return files


def extract_file_type_from_fd(text, file_name):
    """Détermine si un fichier est INPUT ou OUTPUT basé sur le FD"""
    # Chercher le contexte du fichier pour déterminer le type
    if re.search(rf'OPEN\s+INPUT\s+{file_name}\b', text, re.IGNORECASE):
        return 'INPUT'
    elif re.search(rf'OPEN\s+OUTPUT\s+{file_name}\b', text, re.IGNORECASE):
        return 'OUTPUT'
    elif re.search(rf'OPEN\s+EXTEND\s+{file_name}\b', text, re.IGNORECASE):
        return 'EXTEND'
    else:
        return 'UNKNOWN'


def create_graph_from_aggregated_json(json_file_path, output_format='svg'):
    """
    Crée un graphe de dépendances à partir du fichier JSON agrégé.
    
    Args:
        json_file_path: Chemin vers le fichier JSON agrégé
        output_format: Format de sortie ('svg', 'png', 'pdf')
    """
    json_path = Path(json_file_path).resolve()
    
    if not json_path.exists():
        print(f"[ERROR] Fichier non trouvé: {json_file_path}")
        return None
    
    print(f"[READ] Lecture du fichier: {json_path}")
    
    try:
        with open(json_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except Exception as e:
        print(f"[ERROR] Erreur lors de la lecture du fichier JSON: {e}")
        return None
    
    # Extraire le nom du programme et le texte source
    program_name = json_path.stem.replace('-aggregated', '')
    cobol_text = data.get('text', '')
    copybooks_list = data.get('copybooks', [])
    
    print(f"[ANALYZE] Analyse du programme: {program_name}")
    
    # Créer le graphe
    graph = graphviz.Digraph(
        name=f'{program_name}_Dependencies',
        comment=f'Graphe de dépendances complet - {program_name}',
        format=output_format,
        engine='dot'
    )
    
    # Configuration du graphe
    graph.attr(rankdir='LR', bgcolor='white')  # Left to Right
    graph.attr('node', shape='box', style='rounded,filled', fontname='Arial', fontsize='10')
    graph.attr('edge', fontname='Arial', fontsize='8')
    
    stats = {
        'program': 0,
        'input_files': 0,
        'output_files': 0,
        'extend_files': 0,
        'copybooks': len(copybooks_list),
        'jcl_context': False
    }
    
    # ========================================
    # NOEUD PRINCIPAL: Programme COBOL
    # ========================================
    graph.node(
        f'PGM_{program_name}',
        f'📦 {program_name}\n(COBOL Program)',
        fillcolor='#90EE90',
        shape='component',
        style='rounded,filled'
    )
    stats['program'] = 1
    
    # ========================================
    # EXTRACTION DES FICHIERS
    # ========================================
    print(f"   Extraction des fichiers...")
    files = extract_files_from_cobol_text(cobol_text)
    
    # Créer des clusters pour les fichiers
    if files:
        input_files = []
        output_files = []
        extend_files = []
        
        for file_name, file_info in files.items():
            file_type = extract_file_type_from_fd(cobol_text, file_name)
            
            if file_type == 'INPUT':
                input_files.append((file_name, file_info))
                stats['input_files'] += 1
            elif file_type == 'OUTPUT':
                output_files.append((file_name, file_info))
                stats['output_files'] += 1
            elif file_type == 'EXTEND':
                extend_files.append((file_name, file_info))
                stats['extend_files'] += 1
        
        # Cluster pour fichiers INPUT
        if input_files:
            with graph.subgraph(name='cluster_inputs') as inputs:
                inputs.attr(label='📥 INPUT Files', style='filled', color='lightblue', bgcolor='#E8F4F8')
                inputs.attr('node', fillcolor='#B3E5FC', color='#0288D1')
                
                for file_name, file_info in input_files:
                    inputs.node(
                        f'FILE_IN_{file_name}',
                        f'{file_name}\n({file_info["organization"]})',
                        shape='cylinder'
                    )
                    graph.edge(
                        f'FILE_IN_{file_name}',
                        f'PGM_{program_name}',
                        label='READ',
                        color='#0288D1',
                        style='solid'
                    )
        
        # Cluster pour fichiers OUTPUT
        if output_files:
            with graph.subgraph(name='cluster_outputs') as outputs:
                outputs.attr(label='📤 OUTPUT Files', style='filled', color='lightgreen', bgcolor='#E8F5E9')
                outputs.attr('node', fillcolor='#C8E6C9', color='#388E3C')
                
                for file_name, file_info in output_files:
                    outputs.node(
                        f'FILE_OUT_{file_name}',
                        f'{file_name}\n({file_info["organization"]})',
                        shape='cylinder'
                    )
                    graph.edge(
                        f'PGM_{program_name}',
                        f'FILE_OUT_{file_name}',
                        label='WRITE',
                        color='#388E3C',
                        style='solid'
                    )
        
        # Cluster pour fichiers EXTEND
        if extend_files:
            with graph.subgraph(name='cluster_extends') as extends:
                extends.attr(label='📝 EXTEND Files', style='filled', color='lightyellow', bgcolor='#FFFDE7')
                extends.attr('node', fillcolor='#FFF9C4', color='#F57F17')
                
                for file_name, file_info in extend_files:
                    extends.node(
                        f'FILE_EXT_{file_name}',
                        f'{file_name}\n({file_info["organization"]})',
                        shape='cylinder'
                    )
                    graph.edge(
                        f'PGM_{program_name}',
                        f'FILE_EXT_{file_name}',
                        label='EXTEND',
                        color='#F57F17',
                        style='dashed'
                    )
    
    # ========================================
    # COPYBOOKS
    # ========================================
    if copybooks_list:
        print(f"   Trouvé {len(copybooks_list)} copybook(s)")
        with graph.subgraph(name='cluster_copybooks') as cpy:
            cpy.attr(label='📚 Copybooks', style='filled', color='#E1BEE7', bgcolor='#F3E5F5')
            cpy.attr('node', fillcolor='#CE93D8', color='#6A1B9A')
            
            for copybook in copybooks_list:
                cpy.node(
                    f'CPY_{copybook}',
                    f'COPY\n{copybook}',
                    shape='note',
                    style='rounded,filled'
                )
                graph.edge(
                    f'CPY_{copybook}',
                    f'PGM_{program_name}',
                    label='includes',
                    style='dotted',
                    color='#6A1B9A'
                )
    
    # ========================================
    # CONTEXTE JCL (si disponible)
    # ========================================
    if 'jclExecutionContext' in data:
        stats['jcl_context'] = True
        print(f"   Contexte JCL trouvé")
        jcl_context = data['jclExecutionContext']
        job = jcl_context.get('job', {})
        job_name = job.get('name', 'UNKNOWN_JOB')
        
        graph.node(
            'JCL_JOB',
            f'🔵 {job_name}\n(JCL Job)',
            fillcolor='#FFE5B4',
            shape='folder'
        )
        
        graph.edge(
            'JCL_JOB',
            f'PGM_{program_name}',
            label='executes',
            color='#FF6F00',
            style='bold',
            penwidth='2'
        )
        stats['jcl_context'] = 1
    
    # ========================================
    # SAUVEGARDER LE GRAPHE
    # ========================================
    output_dir = json_path.parent
    output_base = output_dir / f'{program_name}_dependency_graph'
    
    print(f"[GENERATE] Génération du graphe...")
    try:
        graph.render(str(output_base), cleanup=True)
        output_file = f"{output_base}.{output_format}"
        print(f"[SUCCESS] Graphe généré avec succès!")
        print(f"   [OUTPUT] Fichier: {output_file}")
        
        # Afficher les statistiques
        print(f"\n[STATS] Statistiques:")
        print(f"   - Programme: {program_name}")
        print(f"   - Fichiers INPUT: {stats['input_files']}")
        print(f"   - Fichiers OUTPUT: {stats['output_files']}")
        print(f"   - Fichiers EXTEND: {stats['extend_files']}")
        print(f"   - Copybooks: {stats['copybooks']}")
        if stats['jcl_context']:
            print(f"   - Contexte JCL: [YES] Présent")
        else:
            print(f"   - Contexte JCL: [NO] Absent")
        
        return output_file
    except Exception as e:
        print(f"[ERROR] Erreur lors de la génération du graphe: {e}")
        import traceback
        traceback.print_exc()
        return None


if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(
        description='Génère un graphe SVG des dépendances AST COBOL'
    )
    parser.add_argument('--input', '-i', required=True, help='Chemin vers le fichier JSON agrégé')
    parser.add_argument('--output', '-o', required=True, help='Chemin de sortie pour le fichier SVG')
    parser.add_argument('--format', '-f', default='svg', help='Format de sortie (svg, png, pdf)')
    
    args = parser.parse_args()
    
    aggregated_file = args.input
    output_format = args.format
    
    print(f"[START] Génération du graphe de dépendances AST COBOL")
    print(f"   Fichier d'entrée: {aggregated_file}")
    print(f"   Fichier de sortie: {args.output}")
    print(f"   Format de sortie: {output_format}")
    print()
    
    try:
        output = create_graph_from_aggregated_json(aggregated_file, output_format=output_format)
        if output:
            # Copier vers le chemin de sortie spécifié
            import shutil
            shutil.copy(output, args.output)
            # Supprimer le fichier SVG original dans le répertoire aggregated
            os.remove(output)
            print(f"\n🎉 Terminé! Fichier généré: {args.output}")
        else:
            sys.exit(1)
    except Exception as e:
        print(f"❌ Erreur fatale: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
