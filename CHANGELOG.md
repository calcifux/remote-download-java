# Changelog

> Read this in other languages: [Español](CHANGELOG-es.md)

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project loosely follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] — 2026-05-10

### Added

- Test suite for the `apache`, `spring` and `quarkus` modules:
  - `remote-download-apache`: 9 JUnit 5 + WireMock tests covering retries,
    bearer / basic auth, custom headers, 4xx / 5xx handling and checksum.
  - `remote-download-spring`: 14 tests covering the `Downloads` static
    factory, the injectable `RemoteDownloadService` and the Spring Boot
    auto-configuration (`ApplicationContextRunner` for properties binding,
    disable toggle and `@ConditionalOnMissingBean`).
  - `remote-download-quarkus`: 10 tests covering the JAX-RS `Downloads`
    static factory and the CDI `RemoteDownloadJaxRsService`, including the
    RFC 5987 UTF-8 `filename*` encoding.
- The CI workflow now runs the full reactor with tests on every push,
  pull request and tag — **50 tests verde** across `core`, `apache`,
  `spring` and `quarkus`.

### Notes

- No breaking changes. Drop-in upgrade from 1.0.0.
- The cloud / file-transfer modules (`s3`, `azure`, `gcs`, `sftp`, `ftp`)
  do not yet ship tests — planned for a future minor release using
  Testcontainers (LocalStack, Azurite, atmoz/sftp, alpine-ftp-server).

[1.0.1]: https://github.com/calcifux/remote-download-java/releases/tag/v1.0.1

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
