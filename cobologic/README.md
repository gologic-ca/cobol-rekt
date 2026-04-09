# 🧠 Cobologic — Agent COBOL Intelligence

Agent GitHub Copilot pour analyser des bases de code COBOL mainframe. Basé sur la spécification [Agent Skills](https://agentskills.io/specification).

## Prérequis

- **smojol-rest-api** en cours d'exécution (port 8080 par défaut)
- **Python 3.10+** avec `httpx` pour les skills complexes (`analyze-call-chain`, `get-statistics`)
- **GitHub Copilot** (Chat ou agent Copilot) avec accès aux skills

## Démarrer l'API REST

```bash
java -Dast.base.path=./out -jar smojol-rest-api/target/smojol-rest-api-1.0.0.jar
```

## Structure

```
cobologic/
├── agent.md                        # Persona et instructions système
├── skills/
│   ├── search-program/SKILL.md     # Recherche un programme COBOL
│   ├── search-copybook/SKILL.md    # Recherche un copybook
│   ├── search-jcl/SKILL.md         # Recherche un JCL
│   ├── search-dataset/SKILL.md     # Recherche un dataset
│   ├── list-programs/SKILL.md      # Liste les programmes (avec filtre)
│   ├── list-copybooks/SKILL.md     # Liste les copybooks
│   ├── list-jcls/SKILL.md          # Liste les JCL
│   ├── list-datasets/SKILL.md      # Liste les datasets
│   ├── find-copybook-usage/SKILL.md           # Programmes utilisant un copybook
│   ├── find-programs-using-dataset/SKILL.md   # Programmes accédant un dataset
│   ├── analyze-dependencies/SKILL.md          # Graphe de dépendances + score de complexité
│   ├── analyze-call-chain/                    # Arbre d'appels récursif
│   │   ├── SKILL.md
│   │   └── scripts/analyze_call_chain.py
│   ├── analyze-impact/SKILL.md     # Impact d'une modification (auto-détection du type)
│   ├── compare-programs/SKILL.md   # Comparaison de deux programmes
│   ├── get-statistics/             # Statistiques globales
│   │   ├── SKILL.md
│   │   └── scripts/get_statistics.py
│   ├── find-unused-copybooks/SKILL.md   # Copybooks jamais utilisés
│   ├── find-orphan-programs/SKILL.md    # Programmes jamais appelés
│   ├── find-complex-programs/SKILL.md   # Top N programmes les plus complexes
│   ├── search-by-pattern/SKILL.md       # Recherche par wildcard (CB*, *VALID*, etc.)
│   └── check-api-health/SKILL.md        # Health check de l'API REST
└── README.md
```

## Skills disponibles (20)

| Skill | Description |
|-------|-------------|
| `search-program` | Détails complets d'un programme (copybooks, callers, callees) |
| `search-copybook` | Informations sur un copybook et ses utilisateurs |
| `search-jcl` | Programmes et datasets d'un JCL |
| `search-dataset` | Programmes et JCL accédant à un dataset |
| `list-programs` | Liste tous les programmes (filtre partiel optionnel) |
| `list-copybooks` | Liste tous les copybooks (triés par usage) |
| `list-jcls` | Liste tous les JCL |
| `list-datasets` | Liste tous les datasets |
| `find-copybook-usage` | Programmes utilisant un copybook + niveau d'impact |
| `find-programs-using-dataset` | Programmes accédant à un dataset |
| `analyze-dependencies` | Graphe complet + score de complexité d'un programme |
| `analyze-call-chain` | Arbre d'appels récursif (gère circularité et programmes externes) |
| `analyze-impact` | Impact d'une modification (auto-détecte le type) |
| `compare-programs` | Compare deux programmes (similarité, diffs, métriques) |
| `get-statistics` | Statistiques globales, top 10 complexité, top 10 copybooks |
| `find-unused-copybooks` | Copybooks jamais utilisés (candidats à suppression) |
| `find-orphan-programs` | Programmes jamais appelés ni référencés en JCL |
| `find-complex-programs` | Top N programmes par complexité (multiple métriques) |
| `search-by-pattern` | Recherche par wildcard `CB*`, `*VALID*`, `*IMPORT` dans tout |
| `check-api-health` | Vérifie la disponibilité de smojol-rest-api |

## Variable d'environnement

```bash
export COBOL_REST_URL=http://localhost:8080  # défaut si non défini
```

## Différence avec le serveur MCP

| | MCP Server | Cobologic Agent Skills |
|-|------------|------------------------|
| Démarrage | Via `.vscode/mcp.json` | Dossier `cobologic/skills/` lu par l'agent |
| Format | Python + FastMCP | Markdown (SKILL.md) + scripts Python optionnels |
| Exécution | Processus stdio séparé | L'agent Copilot exécute les instructions directement |
| Configuration | VSCode settings | Aucune — structure de dossiers |

Le serveur MCP (`smojol_python/src/mcp/mcp_server.py`) reste disponible pour un démarrage en ligne de commande si besoin.
