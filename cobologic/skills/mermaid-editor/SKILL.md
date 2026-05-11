---
name: mermaid-editor
description: "Génère ou modifie des diagrammes Mermaid de façon cohérente et standardisée. À utiliser quand un agent doit créer ou modifier un flowchart, un diagramme de séquence, un diagramme de classes ou tout autre type de diagramme Mermaid. Mots clés : diagramme, mermaid, flowchart, séquence, architecture, flux, schéma."
---

## Comportement général

**À chaque utilisation de ce skill, commencer la réponse par :**
> *"J'utilise le skill **mermaid-editor** pour générer ce diagramme."*

Tu génères des diagrammes Mermaid en respectant **toujours** les conventions
définies dans ce skill. Ces règles s'appliquent à tous les types de diagrammes.

---

## Règles obligatoires

### 1. Noms de variables significatifs
- Les identifiants de noeuds et de participants doivent **toujours** refléter
  leur rôle réel dans le système.
- ❌ `A`, `B`, `node1`, `p1`
- ✅ `utilisateur`, `serviceAuthentification`, `baseDeDonnees`

### 2. Utilisation des couleurs
- Les couleurs servent **uniquement** à regrouper des éléments qui ont le
  **même rôle fonctionnel**.
- Ne jamais utiliser les couleurs à des fins purement esthétiques.
- Exemple de regroupements valides :
  - Tous les services externes → même couleur
  - Tous les composants front-end → même couleur
  - Tous les composants back-end → même couleur

```mermaid
graph TD
    navigateur["Navigateur"]
    applicationWeb["Application Web"]
    serviceAuthentification["Service Authentification"]
    serviceCommande["Service Commande"]
    baseDeDonnees[("Base de Données")]

    navigateur --> applicationWeb
    applicationWeb --> serviceAuthentification
    applicationWeb --> serviceCommande
    serviceCommande --> baseDeDonnees

    classDef frontend fill:#D4E6F1
    classDef backend fill:#D5F5E3
    classDef stockage fill:#FAD7A0

    class navigateur,applicationWeb frontend
    class serviceAuthentification,serviceCommande backend
    class baseDeDonnees stockage
```

### 3. Diagrammes de séquence : toujours utiliser `autonumber`
- Tous les diagrammes de séquence doivent inclure la directive `autonumber`
  pour numéroter automatiquement les étapes.
- Cela facilite les références lors des discussions et des revues.

```mermaid
sequenceDiagram
    autonumber
    actor utilisateur as Utilisateur
    participant serviceAuthentification as Service Authentification
    participant baseDeDonnees as Base de Données

    utilisateur->>serviceAuthentification: Connexion (email, mot de passe)
    serviceAuthentification->>baseDeDonnees: Vérifier les credentials
    baseDeDonnees-->>serviceAuthentification: Résultat de la vérification
    serviceAuthentification-->>utilisateur: Token JWT
```

### 4. Ordre des actions dans un flowchart

Quand un `flowchart` représente une séquence d'événements ordonnés, utiliser les **emoji keycap** (1️⃣2️⃣3️⃣…) en préfixe sur les labels de flèches pour indiquer l'ordre.

- ✅ Préférer les keycap emoji plutôt que les chiffres cerclés Unicode (①②③) — ils sont nettement plus grands et lisibles dans le rendu.
- Ne pas ajouter d'autres emoji décoratifs sur les flèches ou les nœuds — ils surchargent le diagramme sans valeur ajoutée.
- Quand le même numéro s'applique à l'aller et au retour d'un échange (ex : demande + réponse), utiliser le même chiffre sur les deux flèches.

```mermaid
flowchart LR
    subgraph authServer["Authorization Server"]
        entraID["EntraID"]
    end

    subgraph app["Application"]
        serviceA["Service A"]
        serviceB["Service B"]
        serviceA -->|"2️⃣ Appel avec Bearer Token"| serviceB
    end

    serviceA -->|"1️⃣ Get Service Token"| entraID
    entraID -.->|"1️⃣ Service Token (JWT)"| serviceA
```

### 5. Texte sur plusieurs lignes dans un nœud
- Pour afficher du texte sur plusieurs lignes dans un nœud, utiliser `<br>`.
- ❌ `\n` (ne fonctionne pas dans Mermaid)
- ✅ `<br>` (balise HTML reconnue par le moteur de rendu Mermaid)

> **🚫 INTERDIT — zéro tolérance** : La séquence `\n` est **strictement interdite** dans tout label de nœud, d'arête ou de participant Mermaid. Toute occurrence de `\n` dans le code généré est considérée comme une **erreur bloquante** à corriger avant livraison.

```mermaid
graph TD
    serviceCommande["Service Commande<br>v2.3.1"]
    baseDeDonnees[("Base de Données<br>PostgreSQL")]

    serviceCommande --> baseDeDonnees
```

---

## Validation pré-livraison (obligatoire)

Avant de soumettre tout diagramme généré, effectuer mentalement ces vérifications dans l'ordre :

1. **Aucun `\n`** — scanner chaque label de nœud, d'arête et de participant : toute présence de `\n` doit être remplacée par `<br>` (règle 5).
2. **`autonumber` présent** — vérifier que tout `sequenceDiagram` contient la directive `autonumber` (règle 3).
3. **Identifiants significatifs** — aucun identifiant de type `A`, `B`, `node1` (règle 1).
4. **Couleurs par rôle** — vérifier que les `classDef` regroupent uniquement des éléments de même rôle fonctionnel (règle 2).
5. **Ordre dans les flowcharts** — si le diagramme représente une séquence d'événements, vérifier que les keycap emoji sont présents sur les flèches (règle 4).

Si l'une de ces vérifications échoue, **corriger avant de répondre**.

---

## Structure de réponse

Toujours retourner le diagramme dans un bloc de code Mermaid :

```mermaid
graph TD
    ...
```

---

## Rappel des types de diagrammes supportés

| Type | Directive | Règle spéciale |
|---|---|---|
| Flowchart | `graph TD` | Couleurs par rôle fonctionnel |
| Séquence | `sequenceDiagram` | Toujours `autonumber` |
| Classes | `classDiagram` | Noms de classes significatifs |
| État | `stateDiagram-v2` | Couleurs par type d'état |

---

## Diagrammes COBOL (Cobologic)

Quand un skill Cobologic produit des résultats de dépendances, chaîne d'appels
ou analyse d'impact, **toujours générer un diagramme Mermaid** via
`renderMermaidDiagram` en plus de la sortie textuelle.

### Correspondance skill → diagramme

| Skill | Type | Direction | Description |
|---|---|---|---|
| `analyze-dependencies` | `graph TD` | Programme au centre | Copybooks, callees, callers et JCL autour du programme |
| `analyze-call-chain` | `graph TD` | Top-down | Arbre d'appels récursif |
| `analyze-impact` | `graph TD` | Source → cibles | Élément modifié → éléments impactés |
| `find-copybook-usage` | `graph LR` | Gauche → droite | Copybook → programmes qui l'utilisent |
| `find-programs-using-dataset` | `graph LR` | Gauche → droite | Dataset → programmes |
| `compare-programs` | `graph TD` | Deux racines | Deux programmes avec dépendances partagées mises en évidence |

### classDef COBOL obligatoires

Utiliser systématiquement ces classes pour distinguer les types d'entités :

```
classDef default fill:#e8f4fd,stroke:#2196F3
classDef copybook fill:#fff3e0,stroke:#FF9800
classDef caller fill:#e8f5e9,stroke:#4CAF50
classDef jcl fill:#f3e5f5,stroke:#9C27B0
classDef external fill:#ffebee,stroke:#f44336,stroke-dasharray: 5 5
classDef circular fill:#fff9c4,stroke:#FFC107,stroke-dasharray: 3 3
```

### Règles spécifiques COBOL

- Marquer les programmes externes (non trouvés dans le catalogue) avec `:::external`
- Marquer les dépendances circulaires avec `:::circular`
- Garder les labels courts : nom du programme uniquement, pas de chemin
- Pour les chaînes d'appels > 3 niveaux, regrouper les feuilles dans un nœud résumé
- Utiliser des liens pointillés (`-.->`) pour les relations JCL → Programme

### Exemple : analyze-dependencies

```mermaid
graph TD
    CBIMPORT(["📦 CBIMPORT"])
    CBIMPORT --> CPY1["📚 CVACT01Y"]
    CBIMPORT --> CPY2["📚 CVACT02Y"]
    CBIMPORT --> CALLEE1["➡️ SUBPROG1"]
    CALLER1["⬅️ MAINPROG"] --> CBIMPORT
    JCL1["📋 RUNJOB01"] -.-> CBIMPORT
    classDef default fill:#e8f4fd,stroke:#2196F3
    classDef copybook fill:#fff3e0,stroke:#FF9800
    classDef caller fill:#e8f5e9,stroke:#4CAF50
    classDef jcl fill:#f3e5f5,stroke:#9C27B0
    class CPY1,CPY2 copybook
    class CALLER1 caller
    class JCL1 jcl
```

### Exemple : analyze-call-chain

```mermaid
graph TD
    MAINPROG(["📦 MAINPROG"]) --> SUBPROG1["SUBPROG1"]
    MAINPROG --> SUBPROG2["SUBPROG2"]
    SUBPROG1 --> SUBPROG3["SUBPROG3"]
    SUBPROG2 --> EXT1["EXTPROG"]:::external
    classDef external fill:#ffebee,stroke:#f44336,stroke-dasharray: 5 5
```

### Exemple : analyze-impact

```mermaid
graph TD
    MODIFIED["🔧 CVACT01Y<br>(copybook modifié)"]
    MODIFIED --> PROG1["CBIMPORT"]
    MODIFIED --> PROG2["CBEXPORT"]
    MODIFIED --> PROG3["CBVALID"]
    classDef default fill:#e8f4fd,stroke:#2196F3
    classDef high fill:#ffebee,stroke:#f44336
    classDef medium fill:#fff3e0,stroke:#FF9800
    classDef low fill:#e8f5e9,stroke:#4CAF50
    class MODIFIED high
    class PROG1,PROG2 medium
    class PROG3 low
```