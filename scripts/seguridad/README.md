# Seguridad local del backend (no CI)

SAST y SCA se corren **en esta máquina**, no en GitHub Actions.

Este repo cubre Java/Maven. El frontend tiene ESLint security, `npm audit` y DAST (ZAP)
en `Bolsa-de-Trabajo-frontend/scripts/seguridad`. DAST pega al Nginx de la SPA, no a
este JAR; por eso el script ZAP vive allá.

## Prerrequisitos

- JDK 17+ y Maven (perfil `-Psast` en el `pom.xml`, no se engancha a `verify` ni al CI)
- Node.js 20+ **solo** para consultar [OSV.dev](https://osv.dev) (sin Python ni cuenta de OSS Index)
- Trivy es opcional

## Comandos (desde la raíz de este repo)

```powershell
.\scripts\seguridad\sast.ps1
.\scripts\seguridad\sca.ps1
```

Git Bash:

```bash
bash scripts/seguridad/sast.sh
bash scripts/seguridad/sca.sh
```

JSON/XML de cada corrida van a `scripts/seguridad/out/` (ignorado). El resumen para el
docente va en `HALLAZGOS.md`.
