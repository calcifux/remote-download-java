# Changelog

> Léelo en otro idioma: [English](CHANGELOG.md)

Todos los cambios relevantes de este proyecto están documentados en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y el proyecto usa de manera laxa [Versionado Semántico](https://semver.org/lang/es/).

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
