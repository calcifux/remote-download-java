# Changelog

> Léelo en otro idioma: [English](CHANGELOG.md)

Todos los cambios relevantes de este proyecto están documentados en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y el proyecto usa de manera laxa [Versionado Semántico](https://semver.org/lang/es/).

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
