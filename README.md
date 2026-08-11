# Pipeline DevSecOps

Fork de [petclinic](https://github.com/Taller-DevSecOps/pet-clinic) con CI/CD en GitHub Actions que integra SAST, SCA e Image Security. El pipeline *bloquea el avance* ante hallazgos de severidad **Critical/High/Medium** en código, dependencias e imagen Docker.

## Arquitectura

Un workflow, [`.github/workflows/ci-security.yml`](.github/workflows/ci-security.yml), con cinco jobs:

| Job | Nombre en Actions | Etapas |
|---|---|---|
| `build-and-test` | Build & Test | 2 |
| `sast` | SAST (CodeQL) | 3 |
| `sca` | SCA (OWASP Dependency-Check) | 4 |
| `dockerfile` | Dockerfile Audit (Trivy config) | 5 |
| `image` | Image Build, Scan & Publish | 6, 7, 8 |

```mermaid
flowchart LR
    CO[1. Checkout] --> BT[2. Build & Test<br/>mvnw verify]
    BT --> SCA[4. SCA<br/>Dependency-Check]
    SCA --> IMG[6. Image Build]
    IMG --> TR[7. Image Security<br/>Trivy]
    TR --> PUB[8. Image Publish<br/>GHCR]
    CO --> SAST[3. SAST<br/>CodeQL]
    SAST --> IMG
    CO --> DF[5. Dockerfile Audit<br/>Trivy config]
    DF --> IMG
```

| Etapa | Herramienta | Analiza | Falla si |
|---|---|---|---|
| 1. Checkout | `actions/checkout` | — | — |
| 2. Build & Test | `./mvnw verify` (JDK 8, Temurin) | Compila, testea, publica `.jar` como `app-jar` | Falla compilación/test → salta etapas 4 y 6-8 |
| 3. SAST | CodeQL | `src/main` Java | Critical/High/Medium |
| 4. SCA | OWASP Dependency-Check | Dependencias del `.jar` | CVEs Critical/High/Medium |
| 5. Dockerfile Audit | Trivy config | Buenas prácticas del `Dockerfile` | Malas configs Critical/High/Medium |
| 6. Image Build | `docker build` | Empaqueta el `.jar` | — |
| 7. Image Security | Trivy | Paquetes OS de la imagen | CVEs Critical/High/Medium con parche |
| 8. Image Publish | `docker push` a GHCR | — | — |

## Triggers

| Job | `push` | `pull_request → main` | `schedule` |
|---|---|---|---|
| `build-and-test`, `dockerfile` | cualquier rama | ✔ | lunes 06:00 UTC |
| `sast`, `sca`, `image` | solo `main` | ✔ | lunes 06:00 UTC |

- `Schedule semanal`: el análisis por evento no detecta CVEs publicadas después del último commit.
- `build-and-test`/`dockerfile` en todas las ramas: dan feedback temprano.
- `concurrency` + `cancel-in-progress` cancela runs previos de la misma rama
- `paths-ignore` evita disparar el pipeline al editar markdown.

## Detalle por etapa

### 3. SAST — CodeQL
Ejecuta `./mvnw clean compile` y cubre ficheros Java de `src/main`, sin `package-info.java` ni tests, ya que `compile` no alcanza `src/test` (el código de test solo generaría ruido).

**Gate propio**: `sast` no falla por hallazgos — no tiene input de umbral, solo publica alertas en Code scanning y devuelve 0 (falla únicamente si el build o el upload se rompen). Por eso se escribe además el SARIF a disco y un paso posterior lo evalúa con jq contra THRESHOLD=4.0 sobre el security-severity de cada regla (7.0 = solo High/Critical; 9.0 = solo Critical). Job sin `needs`: arranca en paralelo con `build-and-test`, ya que compila por su cuenta y no consume `app-jar`. `image` sí lo declara en sus `needs`, así que un hallazgo en código impide publicar.

### 4. SCA — OWASP Dependency-Check
Descarga `app-jar`, resuelve dependencias contra la NVD (`--enableRetired --disableCentral`).

**Gate**: el default de `--failOnCVSS` es 11 (nunca falla, CVSS máximo es 10), por eso se fija explícito en `--failOnCVSS 4`, alineado al `THRESHOLD` del SAST. `image` depende de `sca` (`needs`), así que un gate en rojo salta las etapas 6 a 8.

### 5. Dockerfile Audit — Trivy config
Evalúa el `Dockerfile` contra políticas, no CVEs. En estos checks, "≥ Medium" equivale en la práctica a *todo menos LOW*. Job sin `needs`: corre en paralelo desde el minuto uno, sin esperar al build.

**Gate** (`exit-code: '1'`): cualquier hallazgo sobre el umbral convierte el escaneo en fallo del step, y `severity` + `limit-severities-for-sarif` fijan ese umbral. El SARIF se publica igual porque el upload lleva `if: always()`. `image` lo declara en sus `needs`, así que una mala configuración impide construir, escanear y publicar la imagen.

### 6. Image Build
Descarga `app-jar` a `target/` (`COPY target/*.jar` en el Dockerfile), construye `spring-petclinic:${{ github.sha }}`. Trivy la lee del daemon local, por eso el build comparte job con las etapas 7 y 8.

### 7. Image Security — Trivy
Cubre lo que otros jobs no ven: paquetes OS de la imagen base.

| Parámetro | Efecto |
|---|---|
| `vuln-type: 'os'` | Evita duplicar hallazgos de la etapa 4 |
| `severity` + `limit-severities-for-sarif: true` | Aplica umbral Critical/High/Medium |
| `ignore-unfixed: true` | Solo CVEs con parche disponible |
| `exit-code: '1'` | Convierte hallazgo en fallo del step |

**Gate** (`exit-code: '1'`): un hallazgo sobre el umbral falla el step y con él el job, así que la imagen queda construida pero nunca llega a publicarse. El SARIF se sube igual por el `if: always()`.

### 8. Image Publish — GHCR
**Nota:** Step pendiente de prueba de implementación

`docker push` a `ghcr.io/daedov/pet-clinic`, etiquetada con el SHA del commit para que cada imagen apunte al código exacto que la produjo. Autentica con el `GITHUB_TOKEN` y `packages: write`; no hace falta ningún secret adicional.

## Configuración segura del pipeline

| Medida | Motivo |
|---|---|
| Actions fijadas por SHA | El mantenedor puede cambiar el código detrás de un tag sin avisar, y ese código accede al workspace y secrets. |
| [Dependabot](.github/dependabot.yml) sobre `github-actions` | Un SHA fijo no recibe arreglos de seguridad por sí solo |
| `persist-credentials: false` en checkout | Evita que el token quede en `.git/config` |
| `permissions` mínimos por job | `contents: read` global; `security-events: write` solo donde se sube SARIF; `packages: write` solo en `image`, que es el único que publica; `actions: read` solo en `sast`, donde lo requiere CodeQL |
| `timeout-minutes` en todos los jobs | Acota un job colgado |
| `retention-days` acotado | `app-jar`: 1 día (intermedio entre jobs). `reportes-test`/`sca-report`: 7 días. El registro durable es Code scanning, no los artifacts |
| `if: always()` en publicaciones | La evidencia se publica aunque el gate rompa el build |