---
name: find-orphan-programs
description: Trouve tous les programmes COBOL qui ne sont jamais appelés par un autre programme et ne sont référencés dans aucun JCL. Ces programmes sont potentiellement obsolètes. Utiliser pour nettoyer la base de code, identifier du code mort, ou préparer une migration.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Find Orphan Programs

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/programs`
3. Filter entries where `callers.length === 0 AND jcls.length === 0`
4. For each orphan, note if it calls other programs (`callees.length > 0`)

## Response Format

> ## 👻 Programmes orphelins
>
> **{orphan_count}** programme(s) sur **{total}** ne sont jamais appelés ni référencés dans un JCL ({percentage}%)
>
> | Programme | Copybooks | Appelle d'autres | JCL  | Plans |
> |-----------|-----------|-----------------|------|-------|
> | PROG1     | N         | ✓/✗             | list | list  |
>
> ⚠️ **Recommandation** : Ces programmes ne sont jamais déclenchés dans ce catalogue. Vérifier s'ils sont :
> - Appelés dynamiquement (CALL variable)
> - Référencés dans des JCL non analysés  
> - Réellement obsolètes et candidats à la suppression

If none found:
> ✅ Aucun programme orphelin détecté — tous les programmes sont appelés ou référencés.

## Examples

**User**: "Y a-t-il des programmes jamais appelés ?"
**User**: "Quels programmes sont potentiellement morts ?"
**User**: "Fais un audit des programmes orphelins"
