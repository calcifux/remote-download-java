# Changelog

> Read this in other languages: [Español](CHANGELOG-es.md)

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project loosely follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] — 2026-05-10

### Added

- Universal API: `RemoteDownload.from(source).writeTo(out)` returns a
  `WriteResult` with bytes transferred, duration and optional checksum.
- Core abstraction `DownloadOrigin` with built-in implementations for HTTP
  (JDK `java.net.http.HttpClient`), enterprise HTTP (Apache HttpClient 5),
  Amazon S3, Azure Blob, Google Cloud Storage, SFTP and FTP / FTPS.
- Progress hooks via `onProgress((read, total) -> ...)`.
- Optional checksum digest computed on the fly via
  `checksum("SHA-256")` (any `MessageDigest` algorithm).
- Spring Boot starter: auto-configuration, `RemoteDownloadProperties`,
  injectable `RemoteDownloadService` and a `Downloads` static factory that
  returns `ResponseEntity<StreamingResponseBody>`.
- Quarkus / JAX-RS / CDI adapter with an injectable
  `RemoteDownloadJaxRsService` and a `Downloads` static factory that returns
  `Response` with `StreamingOutput`.
- `jitpack.yml` pinning the JitPack build to JDK 21.
- GitHub Actions workflow building all modules on push, PR and tag.
- Spring Boot demo application under `examples/spring-boot-demo`.
- Bilingual README (English / Spanish).

### Notes

- Distribution is done through [JitPack](https://jitpack.io/#calcifux/remote-download-java).
- The library targets Java 21 and uses `--release 21` for the compiler.

[1.0.0]: https://github.com/calcifux/remote-download-java/releases/tag/v1.0.0
