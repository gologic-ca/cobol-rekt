# 📦 Installation de l'Extension COBOL Smojol Navigator

Ce guide explique comment installer et utiliser l'extension dans vos projets COBOL.

---

## 🔧 Option 1 : Installation via Package (Production)

**Utilisez cette méthode quand le développement de l'extension est terminé.**

### Prérequis

- Node.js et npm installés
- L'extension compilée et testée

### Étape 1 : Installer l'outil vsce

```bash
npm install -g @vscode/vsce
```

> **Note :** `vsce` (Visual Studio Code Extensions) est l'outil officiel pour packager les extensions VS Code.

### Étape 2 : Compiler l'extension

```bash
cd vscode-cobol-extension
npm install
npm run compile
```

Vérifiez qu'il n'y a pas d'erreurs de compilation.

### Étape 3 : Créer le package .vsix

```bash
vsce package
```

Cette commande crée un fichier `cobol-smojol-navigator-1.0.0.vsix` dans le répertoire courant.

**Sortie attendue :**
```
 INFO  Packaged: cobol-smojol-navigator-1.0.0.vsix
```

### Étape 4 : Installer l'extension dans VS Code

**Méthode A : Via la ligne de commande**
```bash
code --install-extension cobol-smojol-navigator-1.0.0.vsix
```

**Méthode B : Via l'interface VS Code**
1. Ouvrir VS Code
2. `Ctrl+Shift+P` → Taper "Extensions: Install from VSIX..."
3. Sélectionner le fichier `cobol-smojol-navigator-1.0.0.vsix`
4. Recharger VS Code si demandé

### Étape 5 : Vérifier l'installation

1. `Ctrl+Shift+X` pour ouvrir le panneau Extensions
2. Chercher "COBOL Smojol Navigator"
3. L'extension devrait apparaître comme installée

### Étape 6 : Utiliser dans vos projets COBOL

```bash
# Terminal 1 : Démarrer l'API Smojol
cd cobol-rekt/smojol-rest-api
java -Dast.base.path=../out -jar target/smojol-rest-api-1.0.0.jar

# Terminal 2 : Ouvrir votre projet COBOL
cd /path/to/your/cobol/project
code .
```

L'extension est **automatiquement active** dès qu'un fichier `.cbl` ou `.jcl` est ouvert !

### Mettre à jour l'extension

Après avoir modifié le code :

1. Incrémenter la version dans `package.json` :
   ```json
   "version": "1.0.1"
   ```

2. Re-packager et réinstaller :
   ```bash
   npm run compile
   vsce package
   code --install-extension cobol-smojol-navigator-1.0.1.vsix
   ```

### Désinstaller l'extension

```bash
code --uninstall-extension gologic.cobol-smojol-navigator
```

**OU** via l'interface : `Ctrl+Shift+X` → Clic droit sur l'extension → "Uninstall"

---

## 🔗 Option 2 : Lien Symbolique (Développement)

**Utilisez cette méthode pendant le développement actif de l'extension.**

Cette méthode crée un lien entre le dossier d'extensions de VS Code et votre répertoire de développement.

### Avantages

- ✅ Modifications du code immédiatement actives (après compilation)
- ✅ Pas besoin de re-packager à chaque fois
- ✅ Idéal pour le développement itératif
- ✅ Fonctionne dans tous vos projets VS Code

### Étape 1 : Compiler l'extension

```bash
cd vscode-cobol-extension
npm install
npm run compile
```

### Étape 2 : Créer le lien symbolique

#### Windows (PowerShell en Administrateur)

```powershell
# Ouvrir PowerShell en tant qu'Administrateur
# Clic droit sur PowerShell → "Exécuter en tant qu'administrateur"

cd $env:USERPROFILE\.vscode\extensions

New-Item -ItemType SymbolicLink `
  -Name "gologic.cobol-smojol-navigator-1.0.0" `
  -Target "<path_to_cobol-rekt>\cobol-rekt\vscode-cobol-extension"
```

#### Linux / macOS

```bash
cd ~/.vscode/extensions

ln -s /path/to/cobol-rekt/vscode-cobol-extension gologic.cobol-smojol-navigator-1.0.0
```

> **Note :** Le nom du lien doit suivre le format `<publisher>.<name>-<version>` défini dans `package.json`.

### Étape 3 : Recharger VS Code

1. Ouvrir VS Code
2. `Ctrl+Shift+P` → "Developer: Reload Window"

**OU** redémarrer VS Code complètement.

### Étape 4 : Vérifier l'installation

1. `Ctrl+Shift+X` pour ouvrir le panneau Extensions
2. Chercher "COBOL Smojol Navigator"
3. L'extension devrait apparaître comme installée

### Workflow de développement

Avec le lien symbolique en place :

```bash
# 1. Modifier le code dans src/extension.ts ou src/smojolApi.ts

# 2. Recompiler
cd vscode-cobol-extension
npm run compile

# 3. Dans VS Code, recharger la fenêtre
# Ctrl+Shift+P → "Developer: Reload Window"

# Les modifications sont actives !
```

### Compilation automatique en mode watch

Pour recompiler automatiquement à chaque modification :

```bash
cd vscode-cobol-extension
npm run watch
```

Laissez ce terminal ouvert. TypeScript recompilera automatiquement à chaque sauvegarde de fichier.

### Retirer le lien symbolique

#### Windows (PowerShell en Administrateur)

```powershell
cd $env:USERPROFILE\.vscode\extensions
Remove-Item "gologic.cobol-smojol-navigator-1.0.0"
```

#### Linux / macOS

```bash
rm ~/.vscode/extensions/gologic.cobol-smojol-navigator-1.0.0
```

Rechargez ensuite VS Code.

---

## 📁 Configuration dans vos Projets COBOL

Une fois l'extension installée (Option 1 ou 2), configurez vos projets COBOL.

### Créer `.vscode/settings.json` dans votre projet COBOL

```json
{
  "cobolSmojol.apiUrl": "http://localhost:8080",
  "cobolSmojol.searchExclude": [
    "**/node_modules/**",
    "**/build/**",
    "**/out/**",
    "**/target/**"
  ]
}
```

### Si l'API tourne sur un autre port

```json
{
  "cobolSmojol.apiUrl": "http://localhost:9090"
}
```

### Si vous voulez exclure certains dossiers de la recherche

```json
{
  "cobolSmojol.searchExclude": [
    "**/node_modules/**",
    "**/archives/**",
    "**/backup/**",
    "**/legacy/**"
  ]
}
```

---

## 🚀 Utilisation dans un Projet COBOL

### Scénario : Vous avez deux projets

```
C:/projects/
├── cobol-rekt/                    ← Outils Smojol + Extension
│   ├── smojol-rest-api/
│   └── vscode-cobol-extension/
│
└── banking-cobol/                 ← Votre code COBOL
    ├── programs/
    │   ├── CBIMPORT.cbl
    │   └── CBEXPORT.cbl
    ├── copybooks/
    │   ├── CVACT01Y.cpy
    │   └── CVACT02Y.cpy
    └── jcl/
        └── IMPORT.jcl
```

### Workflow complet

**Terminal 1 : Démarrer l'API Smojol**
```bash
cd C:/projects/cobol-rekt/smojol-rest-api
java -Dast.base.path=../out -jar target/smojol-rest-api-1.0.0.jar
```

**Terminal 2 : Ouvrir votre projet COBOL**
```bash
cd C:/projects/banking-cobol
code .
```

**Dans VS Code (projet banking-cobol) :**
1. Ouvrir `programs/CBIMPORT.cbl`
2. **CTRL+Click** sur `COPY CVACT01Y` → Le copybook s'ouvre !
3. **Hover** sur un copybook → Info-bulle avec détails
4. **Shift+F12** sur un copybook → Liste tous les usages

**L'extension cherche automatiquement dans toute la structure du projet** :
- `programs/*.cbl`
- `copybooks/*.cpy`
- `jcl/*.jcl`
- N'importe quelle structure de dossiers !

---

## 🧪 Tester la Connexion API

Dans n'importe quel projet avec l'extension installée :

1. `Ctrl+Shift+P`
2. Taper "COBOL Smojol: Check API Connection"
3. Un message apparaît :
   - ✅ "API Smojol accessible sur http://localhost:8080"
   - ❌ "API Smojol non accessible"

---

## 🐛 Dépannage

### L'extension n'apparaît pas dans la liste

**Option 1 :** Vérifiez que le package est installé :
```bash
code --list-extensions | grep cobol
```

**Option 2 :** Vérifiez le lien symbolique :
```bash
# Windows
ls $env:USERPROFILE\.vscode\extensions | grep cobol

# Linux/Mac
ls ~/.vscode/extensions | grep cobol
```

### Les modifications du code ne sont pas prises en compte (Option 2)

1. Vérifiez que la compilation a réussi :
   ```bash
   npm run compile
   ```

2. Rechargez VS Code :
   ```
   Ctrl+Shift+P → "Developer: Reload Window"
   ```

3. Vérifiez les logs :
   ```
   Ctrl+Shift+P → "Developer: Toggle Developer Tools"
   ```
   Onglet "Console" pour voir les erreurs

### CTRL+Click ne fonctionne pas

1. Vérifiez que l'API est démarrée :
   ```bash
   curl http://localhost:8080/api/health
   ```

2. Vérifiez que les fichiers sont dans le workspace actuel

3. Ouvrez la console de débogage : `Ctrl+Shift+Y`
   - Cherchez les messages 🔍 ou ❌

---

## 📊 Comparaison des Options

| Critère | Option 1 : Package | Option 2 : Symlink |
|---------|-------------------|-------------------|
| **Usage** | Production, distribution | Développement actif |
| **Installation** | Une fois, stable | Une fois, puis oubliez |
| **Mise à jour** | Re-package + réinstall | Compile + reload window |
| **Performance** | Identique | Identique |
| **Partage équipe** | ✅ Distribuer .vsix | ❌ Chacun fait son symlink |
| **Simplicité** | ⭐⭐⭐⭐ | ⭐⭐⭐ |

---

## 🎯 Recommandations

### Pour le développement de l'extension
→ **Utilisez l'Option 2 (Lien Symbolique)**
- Workflow rapide : modifier → compiler → recharger
- Pas de re-packaging à chaque test

### Pour distribuer à l'équipe
→ **Utilisez l'Option 1 (Package .vsix)**
- Créez un package stable
- Partagez le fichier `.vsix`
- Tout le monde installe avec `code --install-extension`

### Pour production / partage externe
→ **Publiez sur le Marketplace VS Code** (voir README.md)

---

## 📞 Support

Si vous rencontrez des problèmes :

1. Vérifiez que l'API Smojol est démarrée
2. Consultez les logs : `Ctrl+Shift+Y` (Debug Console)
3. Testez la connexion API : `Ctrl+Shift+P` → "COBOL Smojol: Check API Connection"
4. Vérifiez que le workspace contient des fichiers `.cbl` ou `.jcl`
