# 🚀 COBOL Smojol Navigator - Extension VS Code

Extension VS Code pour naviguer intelligemment dans le code COBOL en utilisant l'API Smojol MCP. Fournit **Go-to-Definition**, **Hover** et **Find References** pour les copybooks et programmes.

## ✨ Fonctionnalités

### 📋 Navigation dans les Copybooks (fichiers .cbl)

- **CTRL+Click** sur `COPY CVACT01Y` → Ouvre directement le fichier copybook
- **Hover** sur un copybook → Affiche les métadonnées (type, programmes qui l'utilisent)
- **Shift+F12** sur un copybook → Liste tous les programmes qui l'utilisent

### 📄 Navigation dans les Programmes

- **CTRL+Click** sur `CALL 'CBIMPORT'` → Ouvre le fichier COBOL du programme
- **Hover** sur un programme → Affiche les infos (copybooks, appels, complexité)

### ⚙️ Navigation dans les JCL (fichiers .jcl)

- **CTRL+Click** sur `PGM=CBIMPORT` → Ouvre le fichier COBOL du programme
- **Hover** sur un programme JCL → Affiche les détails du programme

---

## 🚀 Quick Start (5 minutes)

### 1️⃣ Installer les dépendances
```bash
cd vscode-cobol-extension
npm install
```

### 2️⃣ Compiler l'extension
```bash
npm run compile
```

### 3️⃣ Démarrer l'API Smojol

Dans un terminal séparé :
```bash
cd ../smojol-rest-api
java -Dast.base.path=../out -jar target/smojol-rest-api-1.0.0.jar
```

Vérifiez que l'API répond :
```bash
curl http://localhost:8080/api/health
# Doit retourner: {"status":"UP"}
```

### 4️⃣ Tester l'extension

Appuyez sur **F5** dans VS Code pour lancer une nouvelle fenêtre avec l'extension chargée.

### 5️⃣ Ouvrir votre projet COBOL

Dans la **nouvelle fenêtre** (Extension Development Host) :
- `File → Open Folder` → Sélectionnez votre répertoire COBOL
- Ouvrez un fichier `.cbl`
- **CTRL+Click** sur `COPY XXXXX` pour tester

---

## 📖 Utilisation

### Exemples avec les fichiers de test

1. Ouvrez `test/sample.cbl`
2. Placez votre curseur sur `COPY CVACT01Y` (ligne 23)
3. **Maintenez CTRL et cliquez** → Le copybook s'ouvre !
4. **Survolez avec la souris** → Une info-bulle apparaît avec les détails

### Exemple avec un JCL

1. Ouvrez `test/sample.jcl`
2. Placez votre curseur sur `PGM=CBIMPORT` (ligne 9)
3. **CTRL+Click** → Le programme COBOL s'ouvre !

### Tableau des actions

| Action | Résultat |
|--------|----------|
| **CTRL+Click** sur `COPY XXXXX` | Ouvre le fichier copybook |
| **CTRL+Click** sur `CALL 'PROG'` | Ouvre le programme COBOL |
| **CTRL+Click** sur `PGM=XXXXX` (JCL) | Ouvre le programme |
| **Hover** sur un copybook | Affiche type, usage, liste des programmes |
| **Hover** sur un programme | Affiche copybooks, calls, complexity |
| **Shift+F12** sur un copybook | Liste tous les usages |

---

## ⚙️ Configuration

Ouvrez les paramètres VS Code (`Ctrl+,`) et cherchez "COBOL Smojol" :

```json
{
  "cobolSmojol.apiUrl": "http://localhost:8080",
  "cobolSmojol.searchExclude": [
    "**/node_modules/**",
    "**/out/**",
    "**/target/**",
    "**/.git/**"
  ]
}
```

### ✨ L'extension fonctionne avec N'IMPORTE QUEL projet !

**Aucune configuration de chemin n'est nécessaire !** 

L'extension cherche automatiquement les fichiers `.cbl`, `.cpy`, et `.jcl` **dans tout votre workspace**, quelle que soit la structure de vos dossiers.

Exemples de structures supportées :
```
✅ Projet A/              ✅ Projet B/              ✅ Projet C/
   src/cobol/               cobol/                    programs/
   copybooks/               copy/                     includes/
   jcl/                     proc/                     jobs/

✅ Projet D/              ✅ Votre projet custom !
   in/cbl/                  n'importe/quelle/
   in/cpy/                  structure/fonctionne/
   in/jcl/
```

### Exclure certains dossiers de la recherche

Si vous voulez exclure certains dossiers (pour améliorer les performances) :

```json
{
  "cobolSmojol.searchExclude": [
    "**/node_modules/**",
    "**/build/**",
    "**/archives/**",
    "**/backup/**"
  ]
}
```

### Si votre API est sur un autre port

```json
{
  "cobolSmojol.apiUrl": "http://localhost:9090"
}
```

---

## 🏗️ Architecture

```
┌────────────────────────────────┐
│     VS Code Extension          │
│                                │
│  ┌──────────────────────────┐ │
│  │  CobolDefinitionProvider │ │ ← CTRL+Click sur copybooks/programs
│  │  CobolHoverProvider      │ │ ← Hover pour info-bulle
│  │  CobolReferenceProvider  │ │ ← Shift+F12 pour references
│  ├──────────────────────────┤ │
│  │  JclDefinitionProvider   │ │ ← CTRL+Click sur PGM=
│  │  JclHoverProvider        │ │ ← Hover dans JCL
│  └────────┬─────────────────┘ │
│           │                    │
│  ┌────────▼─────────────────┐ │
│  │   SmojolApiClient        │ │
│  │   (axios HTTP)           │ │
│  └────────┬─────────────────┘ │
└───────────┼──────────────────────┘
            │ HTTP REST
            ▼
  ┌─────────────────────┐
  │  Smojol REST API    │
  │  localhost:8080     │
  └─────────────────────┘
```

### Composants

- **CobolDefinitionProvider** : Gère le CTRL+Click sur les copybooks et programmes CALL
- **CobolHoverProvider** : Affiche les infos au survol dans les fichiers COBOL
- **CobolReferenceProvider** : Liste les références (Shift+F12)
- **JclDefinitionProvider** : Gère le CTRL+Click sur PGM= dans les JCL
- **JclHoverProvider** : Affiche les infos au survol dans les JCL
- **SmojolApiClient** : Client HTTP pour communiquer avec l'API REST

---

## 📁 Structure du Projet

```
vscode-cobol-extension/
├── src/
│   ├── extension.ts      # Providers (Definition, Hover, References)
│   └── smojolApi.ts      # Client API REST
├── test/
│   ├── sample.cbl        # Fichier COBOL de test
│   └── sample.jcl        # Fichier JCL de test
├── .vscode/
│   ├── launch.json       # Configuration debug
│   └── tasks.json        # Configuration build
├── package.json          # Configuration extension
├── tsconfig.json         # Configuration TypeScript
└── README.md             # Cette documentation
```

---

## 🧪 Tester la Connexion API

Commande : `COBOL Smojol: Check API Connection`

1. `Ctrl+Shift+P` → Tapez "Check API Connection"
2. Affiche ✅ si l'API est accessible, ❌ sinon

---

## 🐛 Dépannage

### L'API ne répond pas

```bash
curl http://localhost:8080/api/health
```

Si ça ne fonctionne pas :
- Vérifiez que l'API Smojol est démarrée
- Vérifiez le port dans les settings VS Code
- Vérifiez les logs de l'API

### Le CTRL+Click ne fonctionne pas

1. Vérifiez que vous êtes dans la fenêtre "Extension Development Host"
2. Vérifiez que votre projet COBOL est ouvert dans cette fenêtre
3. Ouvrez la console de débogage : `Ctrl+Shift+Y`
4. Cherchez les messages commençant par 🔍 ou ❌

### Les copybooks ne s'ouvrent pas

1. Vérifiez que les fichiers `.cpy` sont présents dans votre workspace
2. Vérifiez les chemins configurés dans `cobolSmojol.copybookPaths`
3. L'extension cherche les fichiers avec les extensions : `.cpy`, `.CPY`, `.copy`, `.COPY`

### Les infobulles affichent des données incomplètes

- Les données proviennent de l'AST généré par Smojol
- Certaines métadonnées (comme la taille des copybooks) peuvent être vides dans l'AST
- C'est normal et attendu avec la configuration actuelle

---

## 🔍 Voir les Logs

### Logs de l'extension
- Ouvrez la console de débogage : `Ctrl+Shift+Y`
- Onglet "Debug Console" pour voir les logs de l'extension

### Logs de l'API
- Dans le terminal où l'API Smojol tourne
- Tous les appels HTTP sont loggés

---

## 📦 Prérequis

- **Node.js** 20+ et **npm**
- **API Smojol REST** en cours d'exécution
- **VS Code** 1.85+
- **Workspace COBOL** avec fichiers `.cbl`, `.cpy`, `.jcl`

---

## 📚 Documentation Complète

- **[INSTALLATION.md](INSTALLATION.md)** - Guide d'installation détaillé :
  - 📦 Option 1 : Package .vsix pour production
  - 🔗 Option 2 : Lien symbolique pour développement

---

## 🎯 Workflow de Développement

```
┌─────────────────────────────────────────────────────────┐
│  Fenêtre 1 : VS Code Principal                         │
│  (Développement de l'extension)                         │
│                                                         │
│  vscode-cobol-extension/                                │
│  ├── src/extension.ts    ← Modifier le code            │
│  └── ...                                                │
│                                                         │
│  1. Modifier le code                                   │
│  2. npm run compile                                    │
│  3. Appuyer sur F5                                     │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│  Fenêtre 2 : Extension Development Host                │
│  (Test de l'extension)                                  │
│                                                         │
│  Ouvrir votre projet COBOL ici                         │
│  Tester CTRL+Click, Hover, etc.                        │
│                                                         │
│  Ctrl+R pour recharger après modification              │
└─────────────────────────────────────────────────────────┘
```

---

## 🚀 Fonctionnalités Futures (Roadmap)

- [ ] Autocomplétion des noms de copybooks
- [ ] Autocomplétion des noms de programmes
- [ ] Affichage de la structure des copybooks dans le hover
- [ ] Intégration avec les diagnostics (erreurs, warnings)
- [ ] Support des variables COBOL (Go-to-Definition sur variables)
- [ ] Graphe de dépendances visuel

---

## 📄 Licence

MIT

---

## 🤝 Contribution

Cette extension fait partie du projet Smojol. Pour contribuer :
1. Modifiez le code dans `src/`
2. Compilez avec `npm run compile`
3. Testez avec F5
4. Créez une pull request

---

## 📞 Support

Pour toute question ou problème :
- Consultez la console de débogage (`Ctrl+Shift+Y`)
- Vérifiez les logs de l'API Smojol
- Vérifiez que l'API répond avec `curl http://localhost:8080/api/health`
