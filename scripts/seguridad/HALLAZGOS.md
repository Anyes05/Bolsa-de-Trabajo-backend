# Hallazgos SAST / SCA — backend

Corrida local del **2026-11-23, ~22:45 (America/Montevideo)**. No entra al CI.
Frontend (ESLint, npm audit, Trivy del lockfile, ZAP): `Bolsa-de-Trabajo-frontend/scripts/seguridad/HALLAZGOS.md`.

## Qué se ejecutó

Desde la raíz de este repo:

```powershell
.\scripts\seguridad\sast.ps1
.\scripts\seguridad\sca.ps1
```

| Escaneo | Herramienta | Versión | Alcance | Resultado de la corrida |
| --- | --- | --- | --- | --- |
| SAST | PMD, ruleset `category/java/security.xml`, perfil Maven `-Psast`, `includeTests=false` | Plugin 3.26.0, PMD **7.7.0**. JDK de análisis **17.0.12** (el script apunta `JAVA_HOME` ahí; el `java` del PATH es 1.8) | `src/main/java` | Terminó. **0 violaciones.** |
| SCA | Trivy `fs` scanners `vuln` + `secret` | **0.76.2** (instalado en esta corrida) | `pom.xml` y archivos del repo | Terminó. **140 CVE** (12 critical, 54 high, 58 medium, 16 low). **0 secretos.** |
| SCA | `mvn dependency:list` + POST a `https://api.osv.dev/v1/querybatch` | Maven dependency-plugin 3.8.1, Node v22.23.2 | 165 jars `compile`/`runtime` | Terminó. **27 artefactos** con al menos un GHSA. |

DAST no se corre acá. ZAP está en el frontend. El reintento de las **23:07** contra `https://ccisj.cloud-ip.cc` terminó: **0 FAIL, 10 WARN, 58 PASS**. El high es `Server: nginx/1.30.4`. Detalle en el informe del frontend.

No se usó Semgrep, Python, Trivy contra una imagen Docker del JAR, ni OSS Index (responde 401 sin cuenta).

## Resumen

| Frente | Hallazgo | Veredicto |
| --- | --- | --- |
| PMD security | 0 | Crypto, secretos hardcodeados, random, XXE: limpio en el código propio. |
| OSV.dev | 27 / 165 jars | El parent sigue siendo **Spring Boot 3.3.3** (EOL OSS). |
| Trivy | 140 CVE sobre el mismo árbol, 12 critical | Misma causa, otra base (NVD/GHSA) y otro conteo: Trivy cuenta cada CVE, OSV cuenta artefactos. |
| Secretos en el repo | 0 | Trivy secret scanner. |

La corrección de fondo es subir Boot a **3.5.x**. Quedó **pendiente** de hablarlo con el equipo. No se tocó el parent.

## SAST — PMD

Salida que vale: `out/sast-pmd.xml` (timestamp 2026-11-23T22:47, PMD 7.7.0). El XML solo tiene el elemento raíz: ninguna `<violation>`.

El perfil `-Psast` **no estaba** en el `pom.xml` al empezar. Sin ese perfil, `pmd:pmd` usa el ruleset por defecto. Esa pasada (PMD 7.17.0) marcó **16 avisos que no son de seguridad**:

| Regla | Ruleset | Cant. | Clases |
| --- | --- | --- | --- |
| `UnnecessaryFullyQualifiedName` | Code Style | 8 | `ComunicadoService`, `RetencionPermanenciaService`, `AniversarioService`, `SocioBolsaController` |
| `CheckResultSet` | Best Practices | 6 | `AdminPostulanteController`, `CvService`, `PostulanteDatosPersonalesController`, `SocioBolsaController` |
| `CollapsibleIfStatements` | Design | 2 | `PadronExcelParser` |

Se volvió a dejar el perfil en el `pom` (solo se activa con `-Psast`, no entra a `verify` ni al CI) y se repitió el escaneo con `category/java/security.xml`. **Esa es la corrida SAST: 0.**

PMD no mira autorización en runtime (roles, CV por postulación, rate limit). Eso está en `docs/SSDLC.md` y en las pruebas de `SecurityConfig`.

## SCA — OSV.dev

`mvn dependency:list` escribió las coordenadas; `scripts/seguridad/osv-maven.mjs` consultó OSV en lotes de 40. Salida: `out/sca-osv.json`.

**165 artefactos, 27 con GHSA.** Lista completa de la corrida:

| Artefacto | GHSA (cantidad) |
| --- | --- |
| `tomcat-embed-core` 10.1.28 | 37 |
| `spring-webmvc` 6.1.12 | 15 |
| `netty-codec-http` 4.1.112.Final | 18 |
| `jackson-databind` 2.17.2 | 12 |
| `netty-codec-http2` 4.1.112.Final | 8 |
| `logback-core` 1.5.7 | 6 |
| `jackson-core` 2.17.2 | 4 |
| `netty-handler` 4.1.112.Final | 6 |
| `spring-security-web` 6.3.3 | 4 |
| `spring-data-commons` 3.3.3 | 3 |
| `spring-expression` 6.1.12 | 3 |
| `netty-codec` 4.1.112.Final | 3 |
| `spring-boot` 3.3.3 | 2 |
| `spring-core` 6.1.12 | 2 |
| `spring-web` 6.1.12 | 2 |
| `spring-context` 6.1.12 | 2 |
| `spring-security-core` 6.3.3 | 2 |
| `netty-common` 4.1.112.Final | 2 |
| `spring-boot-autoconfigure` 3.3.3 | 1 |
| `spring-security-crypto` 6.3.3 | 1 |
| `spring-boot-starter-actuator` 3.3.3 | 1 |
| `micrometer-core` 1.13.3 | 1 |
| `postgresql` 42.7.3 | 1 |
| `guava` 33.1.0-jre | 1 |
| `poi-ooxml` 5.3.0 | 1 |
| `commons-lang3` 3.14.0 | 1 |
| `log4j-api` 2.23.1 | 1 |

Los jars suben juntos con el parent de Boot. No conviene pinnearlos a mano.

## SCA — Trivy

`trivy fs --scanners vuln,secret` sobre este repo. Una fila: `pom.xml`, tipo `pom`.
**140** (CRITICAL 12, HIGH 54, MEDIUM 58, LOW 16). Secretos: **0**. JSON: `out/sca-trivy.json`.

Artefactos con al menos un CRITICAL:

| Artefacto | CVE critical que reportó | Nota |
| --- | --- | --- |
| `tomcat-embed-core` 10.1.28 | 8, entre ellos CVE-2025-24813 (RCE / divulgación en Tomcat) | Sube con Boot 3.5. |
| `spring-webmvc` 6.1.12 | CVE-2026-47884 (RCE vía el stack de Spring MVC, según el título NVD) | La UI es Vue; igual el jar está en el proceso. Sube con Boot. |
| `netty-handler` 4.1.112.Final | CVE-2026-75595 (bypass de ruteo SNI con ClientHello fragmentado) | TLS del cliente Netty. Sube con Boot. |
| `spring-security-web` 6.3.3 | CVE-2024-38821 (bypass de estáticos en **WebFlux**) | **No aplica:** la API es servlet, no WebFlux. |

HIGH que ya veníamos viendo y siguen: `jackson-databind` / `jackson-core` 2.17.2, `netty-codec-http` y `netty-codec-http2`, `spring-expression`, `spring-data-commons`, `spring-boot` 3.3.3, `postgresql` 42.7.3, `guava`, `micrometer-core`, `spring-security-crypto` (longitud de clave; la app ya exige 8+).

Trivy cuenta cada CVE. OSV cuenta cada artefacto. Por eso 140 y 27 describen el mismo Boot 3.3.3.

## Notas

- Perfil `sast` restaurado en `pom.xml` para que la próxima corrida no vuelva al ruleset de estilo.
- El JDK 21 declarado en el `pom` no es el que usó Maven en esta máquina. PMD igual analizó las fuentes.
- No se cambió `spring-boot-starter-parent` 3.3.3.
