# Changelog

> Léelo en otro idioma: [English](CHANGELOG.md)

Todos los cambios relevantes de este proyecto están documentados en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y el proyecto usa de manera laxa [Versionado Semántico](https://semver.org/lang/es/).

## [1.0.4] — 2026-05-11

### Corregido

- **Infraestructura de build**: se baja `flatten-maven-plugin` de `1.6.0`
  a `1.5.0`. La línea 1.6.x requiere Maven 3.6.3+, lo cual rompió el
  build de v1.0.3 en JitPack (su VM de build trae un Maven más viejo).
  La 1.5.0 es compatible con Maven 3.0+ y ofrece la misma funcionalidad
  para nuestra configuración `flattenMode=resolveCiFriendliesOnly`.

### Notas

- Sin cambios de código respecto a v1.0.3. Actualización directa.
- Es el primer release que JitPack publica correctamente con la nueva
  estructura de POM single-source-of-truth basada en `${revision}`.

[1.0.4]: https://github.com/calcifux/remote-download-java/releases/tag/v1.0.4

## [1.0.3] — 2026-05-11

### Agregado

- **Integración con SonarCloud** en el workflow de CI. El scanner de
  Maven sube cobertura + análisis en cada push y pull request. El quality
  gate es el `Sonar way` (default), actualmente **Passed**.
- **Quality gate con JaCoCo `check`** en el parent pom — rompe el build
  de Maven si algún BUNDLE con pruebas cae bajo 95% LINE / 70% BRANCH.
  Los módulos sin pruebas saltean automáticamente.
- **Dos badges nuevos** en los READMEs: `Quality Gate Status` y `Coverage`
  con valores en vivo desde SonarCloud.
- **2 pruebas nuevas** llevando la suite a **97 pruebas verde**:
  - `core/HttpOriginTest.parseFilenameReturnsNullWhenHeaderHasNoFilenameParam`
  - `core/StreamWriterTest.blankAlgorithmIsTreatedAsNoChecksum`

### Cambiado

- **`remote-download-apache`**: migrado a la API moderna de Apache
  HttpClient 5 — `ConnectionConfig` + `PoolingHttpClientConnectionManagerBuilder`
  para connect timeout, `HttpClientBuilder.setProxy(...)` para routing y
  `executeOpen(...)` para la request. Elimina 3 de 4 warnings de
  deprecation. El 4to (`NTCredentials` constructor de 5 args) queda
  marcado con `@SuppressWarnings("deprecation")` pendiente del bump a
  HC 5.4.
- **`remote-download-quarkus`**: `encodeFilename` ahora delega en
  `URLEncoder.encode(...).replace("+", "%20")`. Mismo output RFC 5987,
  una línea, sin branches por rango ASCII.
- **Cleanup lambdas** en `apache`, `ftp`, `gcs` y `sftp` ahora hacen
  log en nivel `DEBUG` en vez de tragarse las excepciones del close.
- **`HttpHeaderUtils`** en `apache` ahora es `final` con constructor
  privado (higiene de utility class).
- **`RemoteDownloadService.resolveContentType(DownloadOrigin)`**
  simplificado a `defaultContentType()` — se quitó el parámetro que no
  se usaba; el método es privado y el cuerpo retornaba una constante.

### Corregido

- **`apache/HttpHeaderUtils.parseFilename`**: quitaba las comillas ANTES
  de cortar en el separador de parámetros, así que headers tipo
  `Content-Disposition: attachment; filename="x.pdf"; size=42` devolvían
  `"x.pdf"` (con comillas literales) en lugar de `x.pdf`. El nuevo
  orden es: cortar en `;` → quitar prefijo RFC 5987 → quitar comillas.

### Calidad

- **Issues abiertos en SonarCloud: 0** (51 auto-arreglados por los
  refactors arriba, 8 revisados y aceptados con justificación documentada
  — ver `ROADMAP.md` para el rationale por regla).
- **Cobertura**: 98.9% LINE / 91.9% BRANCH / 100% METHOD (JaCoCo local).
  Agregada en SonarCloud: 97.5%.

[1.0.3]: https://github.com/calcifux/remote-download-java/releases/tag/v1.0.3

## [1.0.2] — 2026-05-11

### Agregado

- **Cobertura con JaCoCo** integrada en el parent pom — cada módulo genera
  `target/jacoco.exec` y `target/site/jacoco/index.html` al correr
  `mvn verify`.
- **Quality gate** que exige 95% LINE / 70% BRANCH por BUNDLE. Los módulos
  sin pruebas saltean automáticamente; el día que tengan su primera
  prueba, el gate aplica.
- `lombok.config` en la raíz del proyecto con
  `lombok.addLombokGeneratedAnnotation = true` para que JaCoCo excluya
  automáticamente el bytecode generado por Lombok (`@Getter`, `@Builder`,
  etc.).
- **45 pruebas nuevas** llevando la suite a **95 pruebas verde** entre
  `core` (43), `apache` (20), `spring` (17) y `quarkus` (15).

### Cambiado

- `remote-download-quarkus`: `encodeFilename` reescrito para delegar en
  `URLEncoder.encode(...)` en lugar de iterar byte-por-byte sobre el set
  unreserved de RFC 5987. Mismo output, código más simple, 12 branches
  sintéticos menos en el reporte de cobertura.

### Corregido

- `remote-download-apache`: `HttpHeaderUtils.parseFilename` quitaba las
  comillas ANTES de cortar en el separador de parámetros `;`. El bug se
  manifestaba con headers tipo
  `Content-Disposition: attachment; filename="x.pdf"; size=42`, que
  devolvían `"x.pdf"` (con comillas literales) en lugar de `x.pdf`. El
  nuevo orden de operaciones es: cortar en `;` → quitar prefijo RFC 5987
  → quitar comillas.

### Baseline de cobertura

```
Módulo                 Line %   Branch %   Method %
─────────────────────────────────────────────────────
remote-download-core   100.0%    96.4%     100.0%
remote-download-apache  99.1%    95.0%      95.0%
remote-download-spring 100.0%    80.0%     100.0%
remote-download-quarkus 100.0%    75.0%     100.0%
─────────────────────────────────────────────────────
TOTAL                   99.7%    91.9%      98.9%
```

[1.0.2]: https://github.com/calcifux/remote-download-java/releases/tag/v1.0.2

## [1.0.1] — 2026-05-10

### Agregado

- Suite de pruebas para los módulos `apache`, `spring` y `quarkus`:
  - `remote-download-apache`: 9 pruebas con JUnit 5 + WireMock que cubren
    reintentos, autenticación bearer y básica, headers personalizados,
    manejo de 4xx / 5xx y checksum.
  - `remote-download-spring`: 14 pruebas que cubren la utility class
    `Downloads`, el bean inyectable `RemoteDownloadService` y la
    auto-configuración de Spring Boot (`ApplicationContextRunner` para
    el binding de propiedades, el toggle de habilitado y
    `@ConditionalOnMissingBean`).
  - `remote-download-quarkus`: 10 pruebas para la utility class
    `Downloads` de JAX-RS y el bean CDI `RemoteDownloadJaxRsService`,
    incluyendo la codificación RFC 5987 UTF-8 para `filename*`.
- El workflow de CI corre ahora el reactor completo con pruebas en cada
  push, pull request y tag — **50 pruebas verde** entre `core`, `apache`,
  `spring` y `quarkus`.

### Notas

- Sin cambios incompatibles. Actualización directa desde 1.0.0.
- Los módulos de cloud / file-transfer (`s3`, `azure`, `gcs`, `sftp`,
  `ftp`) todavía no incluyen pruebas — están planeadas para una
  versión menor futura usando Testcontainers (LocalStack, Azurite,
  atmoz/sftp, alpine-ftp-server).

[1.0.1]: https://github.com/calcifux/remote-download-java/releases/tag/v1.0.1

## [1.0.0] — 2026-05-10

### Agregado

- API universal: `RemoteDownload.from(source).writeTo(out)` regresa un
  `WriteResult` con bytes transferidos, duración y checksum opcional.
- Abstracción central `DownloadOrigin` con implementaciones built-in para HTTP
  (JDK `java.net.http.HttpClient`), HTTP enterprise (Apache HttpClient 5),
  Amazon S3, Azure Blob, Google Cloud Storage, SFTP y FTP / FTPS.
- Hooks de progreso vía `onProgress((read, total) -> ...)`.
- Checksum opcional calculado al vuelo vía `checksum("SHA-256")` (cualquier
  algoritmo soportado por `MessageDigest`).
- Spring Boot starter: auto-configuración, `RemoteDownloadProperties`,
  bean inyectable `RemoteDownloadService` y la utility class `Downloads`
  que regresa `ResponseEntity<StreamingResponseBody>`.
- Adapter Quarkus / JAX-RS / CDI con bean inyectable
  `RemoteDownloadJaxRsService` y la utility class `Downloads` que regresa
  `Response` con `StreamingOutput`.
- `jitpack.yml` que fuerza el build de JitPack a JDK 21.
- Workflow de GitHub Actions que compila los módulos en cada push, PR y tag.
- Aplicación de ejemplo en Spring Boot bajo `examples/spring-boot-demo`.
- README bilingüe (inglés / español).

### Notas

- La distribución se hace vía [JitPack](https://jitpack.io/#calcifux/remote-download-java).
- La librería apunta a Java 21 y usa `--release 21` en el compilador.

[1.0.0]: https://github.com/calcifux/remote-download-java/releases/tag/v1.0.0
