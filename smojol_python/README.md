# 🔧 MCP COBOL Search Server

Serveur MCP (Model Context Protocol) pour interroger et analyser du code COBOL via Claude Desktop ou VS Code Copilot Chat.

## 🚀 Démarrage rapide

### 1. Prérequis

- **Python 3.10+**
- **API REST Smojol** en cours d'exécution (port 8080)
- **VS Code** avec Copilot Chat

### 2. Installation

```bash
cd smojol_python
pip install -r requirements.txt
```

### 3. Démarrer l'API REST

```bash
cd smojol-rest-api
java -Dast.base.path=../out -jar target/smojol-rest-api-1.0.0.jar
```



---

## 📊 Outils disponibles (20 au total)

### 🔍 Recherche
- **`search_program`** - Détails complets d'un programme (copybooks, callers, callees)
- **`search_copybook`** - Informations sur un copybook et ses utilisateurs
- **`search_dataset`** - Programmes et JCL utilisant un dataset
- **`search_jcl`** - Programmes exécutés dans un JCL

### 📋 Listage
- **`list_programs`** - Liste tous les programmes (avec filtre optionnel)
- **`list_copybooks`** - Liste tous les copybooks (avec statistiques d'usage)
- **`list_jcls`** - Liste tous les JCL
- **`list_datasets`** - Liste tous les datasets

### 🔬 Analyse de dépendances
- **`analyze_dependencies`** - Calcule le `complexity_score` d'un programme
- **`find_copybook_usage`** - Tous les programmes utilisant un copybook
- **`find_programs_using_dataset`** - Programmes accédant à un dataset
- **`analyze_call_chain`** - Arbre d'appels récursif (gère les callees externes)

### ⚠️ Analyse d'impact
- **`analyze_impact`** - Évalue le `risk_level` de modification d'un élément
- **`compare_programs`** - Compare deux programmes (similarité, différences)

### 📈 Statistiques & Découverte
- **`get_statistics`** - Totaux globaux et top 10 des programmes complexes
- **`find_unused_copybooks`** - Copybooks jamais utilisés (candidates à suppression)
- **`find_orphan_programs`** - Programmes jamais appelés ni référencés
- **`find_complex_programs`** - Top N des programmes par complexité
- **`search_by_pattern`** - Recherche par wildcard (`CB*`, `*VALID`, etc.)

### 🩺 Monitoring
- **`get_cobol_status`** - Health check de l'API REST

---

## ⚙️ Configuration VS Code

### Configuration locale par projet

Créez un fichier `.vscode/mcp.json` dans votre projet avec :

```json
{
  "servers": {
    "cobol-search": {
      "command": "python",
      "args": ["-m", "src.mcp.mcp_server"],
      "cwd": "${workspaceFolder}/smojol_python",
      "env": {
        "COBOL_REST_URL": "http://localhost:8080"
      }
    }
  }
}
```

**Pour ce projet** : `.vscode/mcp.json` est déjà configuré.

**Pour d'autres projets** : Copiez ce fichier dans `.vscode/mcp.json` de votre projet, en remplaçant `${workspaceFolder}/smojol_python` par le chemin absolu :

```json
"cwd": "<path_to_cobol-rekt>/cobol-rekt/smojol_python"
```

**Recharger VS Code** : `Ctrl+Shift+P` → "Developer: Reload Window"

---

## 💬 Exemples d'utilisation

### Recherche simple
```
"Cherche le programme CBACT01C"
→ Retourne: path, copybooks, callers, callees, JCLs
```

### Analyse d'impact
```
"Si je modifie le copybook CVACT01Y, quel est l'impact ?"
→ Calcule risk_level, liste les programmes affectés
```

### Statistiques
```
"Montre-moi les 10 programmes les plus complexes"
→ Classement par complexity_score avec détails
```

### Navigation
```
"Quels programmes utilisent le dataset CUSTOUT ?"
→ Liste des programmes COBOL et JCL accédant au dataset
```

### Découverte
```
"Trouve les copybooks inutilisés"
→ Liste avec pourcentage d'inutilisation
```

---

## 📊 Métriques clés

### 1. **Complexity Score** (Score de complexité)
Mesure la difficulté de maintenance d'un programme.

**Formule** : `copybooks×2 + callees×3 + callers×1`

| Score | Interprétation |
|-------|----------------|
| 0-10 | Simple |
| 11-30 | Moyen |
| 31-50 | Élevé |
| 51+ | Très élevé (candidat au refactoring) |

**Utilisé dans** : `analyze_dependencies`, `find_complex_programs`, `get_statistics`

### 2. **Impact Level** (Niveau d'impact)
Indique le degré d'utilisation d'un élément.

| Utilisations | Impact Level |
|--------------|--------------|
| ≤2 | low |
| 3-5 | medium |
| >5 | high |

**Utilisé dans** : `search_copybook`, `search_dataset`, `find_copybook_usage`

### 3. **Risk Level** (Niveau de risque)
Évalue le risque de modification d'un élément.

**Seuils par type** :
- **Copybook** : critical (>10), high (>5), medium
- **Programme** : high (>5), medium (>2), low
- **Dataset** : critical (>15), high (>8), medium

**Utilisé dans** : `analyze_impact` (avec option transitive)

Voir [METRICS_DOCUMENTATION.md](METRICS_DOCUMENTATION.md) pour les détails complets.

---

## 🐛 Dépannage

### L'API ne répond pas
```bash
curl http://localhost:8080/api/health
# Devrait retourner: {"status":"OK","service":"SmojolRestAPI"}
```

### Module not found
```bash
pip install -r requirements.txt
python -c "import fastmcp, httpx, pydantic; print('✅ OK')"
```

### Copilot ne voit pas les outils

1. Vérifier que `.vscode/mcp.json` existe dans votre projet
2. Vérifier que le chemin `cwd` pointe vers le bon dossier `smojol_python`
3. Recharger VS Code : `Ctrl+Shift+P` → "Developer: Reload Window"
4. Vérifier les logs : `Ctrl+Shift+P` → "Developer: Show Logs" → "Extension Host"

### Outils visibles mais erreurs d'exécution
```bash
# Test direct
python -m src.mcp.mcp_server

# Vérifier API
curl http://localhost:8080/api/programs
```

---

## 📐 Architecture

```
┌─────────────────────┐
│  VS Code Copilot    │
│  Chat               │
└──────────┬──────────┘
           │ stdio
           ▼
┌─────────────────────┐
│  Python MCP Server  │  ← src/mcp/mcp_server.py
│  (FastMCP)          │     20 outils exposés
└──────────┬──────────┘
           │ HTTP (httpx)
           ▼
┌─────────────────────┐
│  Java REST API      │  ← Javalin sur :8080
│  (Smojol)           │
└──────────┬──────────┘
           │ File I/O
           ▼
┌─────────────────────┐
│  AST JSON files     │  ← ./out/*.json
└─────────────────────┘
```

---

## 🔄 Workflow de développement

1. **Modifier** `src/mcp/mcp_server.py`
2. **Recharger VS Code** : `Ctrl+Shift+P` → "Developer: Reload Window"
3. **Valider** dans Copilot Chat avec vos requêtes

Pas besoin de redémarrer l'API Java entre les modifications.

---

## 📁 Structure du projet

```
smojol_python/
├── README.md                      ← Ce fichier
├── METRICS_DOCUMENTATION.md       ← Détails des 3 métriques
├── requirements.txt               ← fastmcp, httpx, pydantic
├── src/
│   └── mcp/
│       └── mcp_server.py         ← Serveur MCP (20 outils)
└── .vscode/
    └── mcp.json                  ← Config VS Code (workspace parent)
```

---

## 🎯 Cas d'usage recommandés

### 1. Analyse d'impact avant modification
```
"Analyse l'impact de modifier le copybook CVACT01Y"
→ risk_level, programmes affectés, recommandations
```

### 2. Identification de code complexe
```
"Trouve les 10 programmes les plus complexes"
→ Candidats au refactoring avec scores
```

### 3. Nettoyage du code
```
"Trouve les copybooks jamais utilisés"
"Trouve les programmes orphelins"
→ Candidates à suppression après validation
```

### 4. Navigation dans les dépendances
```
"Montre la chaîne d'appels de CBIMPORT"
→ Arbre récursif avec distinction internal/external
```

### 5. Recherche par pattern
```
"Tous les programmes de validation"
→ search_by_pattern("*VALID*")
```

---

## 📚 Documentation complémentaire

- **Métriques détaillées** : [METRICS_DOCUMENTATION.md](METRICS_DOCUMENTATION.md)
- **Configuration VS Code** : `.vscode/mcp.json` (workspace parent)

---

## 📝 Notes importantes

- ✅ **Pas de venv requis** : Installation globale de pip suffit
- ✅ **Pas de cache** : Données toujours fraîches depuis l'API
- ✅ **API doit rester active** : Le MCP fait des appels HTTP en temps réel
- ✅ **Gestion des callees externes** : CEE3ABD, COBDATFT marqués comme "external"

---

**Version** : 1.0  
**Dernière mise à jour** : 29 janvier 2026  
**Statut** : Production-ready ✅
