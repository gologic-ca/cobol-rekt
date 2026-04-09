---
name: find-unused-copybooks
description: Trouve tous les copybooks qui ne sont utilisés par aucun programme COBOL dans le catalogue. Ces copybooks sont des candidats à la suppression ou à l'archivage. Utiliser pour nettoyer la base de code, identifier les copybooks obsolètes, ou faire un audit de la base de code.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Find Unused Copybooks

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/copybooks`
3. Filter entries where `usedBy.length === 0`
4. Compute percentage: `unused.length / total.length * 100`

## Response Format

> ## 📚 Copybooks inutilisés
>
> **{unused_count}** copybook(s) sur **{total}** ne sont utilisés par aucun programme ({percentage}%)
>
> Liste :
> - COPYBOOK1
> - COPYBOOK2
> - ...
>
> ⚠️ **Recommandation** : Vérifier si ces copybooks sont référencés en dehors de ce catalogue avant suppression (ex: dans des sous-systèmes non analysés).

If no unused copybooks found:
> ✅ Tous les copybooks sont utilisés par au moins un programme.

## Examples

**User**: "Y a-t-il des copybooks inutilisés ?"
**User**: "Quels copybooks pourrais-je supprimer ?"
**User**: "Fais un audit des copybooks orphelins"
