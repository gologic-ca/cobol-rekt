---
name: check-api-health
description: Vérifie que l'API REST COBOL (smojol-rest-api) est disponible et répond correctement. Retourne le nombre de programmes et copybooks chargés. Utiliser quand l'utilisateur veut savoir si le système est opérationnel, ou en début de session pour vérifier la connectivité.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Check API Health

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/health`
3. Also fetch `GET {COBOL_REST_URL}/api/programs` to count loaded programs
4. Also fetch `GET {COBOL_REST_URL}/api/copybooks` to count loaded copybooks

## Response Format

If healthy:
> ✅ **Cobologic est opérationnel**
> - API : `{COBOL_REST_URL}`
> - Programmes chargés : **{N}**
> - Copybooks chargés : **{N}**

If unreachable:
> ❌ **API indisponible** — `{COBOL_REST_URL}` ne répond pas
> 
> Démarrer l'API avec :
> ```bash
> java -Dast.base.path=./out -jar smojol-rest-api/target/smojol-rest-api-1.0.0.jar
> ```

## Examples

**User**: "Est-ce que le système fonctionne ?"
**User**: "Cobologic est-il connecté ?"
**Action**: check health endpoint and report status
