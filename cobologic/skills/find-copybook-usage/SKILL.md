---
name: find-copybook-usage
description: Trouve tous les programmes COBOL qui utilisent un copybook donné et évalue le niveau d'impact d'une modification. Utiliser quand l'utilisateur veut savoir quels programmes seraient affectés si un copybook changeait, ou faire une analyse d'impact ciblée sur un copybook.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Find Copybook Usage

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/copybooks/{copybook_name}`
3. Extract the `usedBy` array
4. Classify impact level:
   - `usedBy.length > 10` → 🔴 **critical**
   - `usedBy.length > 5` → 🟠 **high**
   - `usedBy.length > 2` → 🟡 **medium**
   - else → 🟢 **low**

## Response Format

> **Copybook** `{name}` est utilisé par **{N}** programme(s) — Impact : {level}

List each program. If impact is high or critical, add:
> ⚠️ Toute modification de ce copybook nécessitera de recompiler ces programmes et d'effectuer des tests de régression.

## Examples

**User**: "Qui utilise CVACT01Y ?"
**Action**: `GET http://localhost:8080/api/copybooks/CVACT01Y`

**User**: "Combien de programmes seraient impactés si je modifie CUSTDATA ?"
**Action**: fetch copybook → count and classify impact
