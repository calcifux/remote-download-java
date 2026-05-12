# Changelog

> Read this in other languages: [Español](CHANGELOG-es.md)

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project loosely follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.4] — 2026-05-11

### Fixed

- **Build infrastructure**: downgrade `flatten-maven-plugin` from `1.6.0`
  to `1.5.0`. The 1.6.x line requires Maven 3.6.3+, which broke the v1.0.3
  build on JitPack (their build VM ships an older Maven). The 1.5.0 line
  is Maven 3.0+ compatible and provides identical functionality for our
  `flattenMode=resolveCiFriendliesOnly` configuration.

### Notes

- No code changes vs. v1.0.3. Drop-in upgrade.
- This is the first release that JitPack publishes successfully with the
  `${revision}` single-source-of-truth POM layout.

[1.0.4]: https://github.com/calcifux/remote-download-java/releases/tag/v1.0.4

## [1.0.3] — 2026-05-11

### Added

- **SonarCloud integration** wired into the CI workflow. The Maven scanner
  uploads coverage + analysis on every push and pull request. Quality gate
  is `Sonar way` (default), currently **Passed**.
- **Quality gate enforced via JaCoCo `check`** in the parent pom — fails
  the Maven build if any tested BUNDLE drops below 95% LINE / 70% BRANCH.
  Modules without tests are skipped automatically.
- **Two new badges** in the READMEs: `Quality Gate Status` and `Coverage`
  pulling live values from SonarCloud.
- **2 new unit tests** raising the suite to **97 tests verde**:
  - `core/HttpOriginTest.parseFilenameReturnsNullWhenHeaderHasNoFilenameParam`
  - `core/StreamWriterTest.blankAlgorithmIsTreatedAsNoChecksum`

### Changed

- **`remote-download-apache`**: migrated to the modern Apache HttpClient 5
  API — `ConnectionConfig` + `PoolingHttpClientConnectionManagerBuilder`
  for connect timeouts, `HttpClientBuilder.setProxy(...)` for routing, and
  `executeOpen(...)` for the request. Removes 3 of 4 deprecation warnings.
  The 4th (`NTCredentials` 5-arg ctor) is tracked for the next HC 5.4 bump
  with `@SuppressWarnings("deprecation")`.
- **`remote-download-quarkus`**: `encodeFilename` now delegates to
  `URLEncoder.encode(...).replace("+", "%20")`. Same RFC 5987 output,
  one line, no per-byte ASCII range branches.
- **Cleanup lambdas** in `apache`, `ftp`, `gcs`, `sftp` now log at
  `DEBUG` level instead of silently swallowing the close exceptions.
- **`HttpHeaderUtils`** in `apache` is now `final` with a private
  constructor (utility class hygiene).
- **`RemoteDownloadService.resolveContentType(DownloadOrigin)`** simplified
  to `defaultContentType()` — the unused parameter was removed; the method
  is private and the body returned a constant.

### Fixed

- **`apache/HttpHeaderUtils.parseFilename`**: was stripping surrounding
  quotes BEFORE cutting at the parameter separator, so headers like
  `Content-Disposition: attachment; filename="x.pdf"; size=42` returned
  `"x.pdf"` (with literal quotes) instead of `x.pdf`. The order is now:
  cut at `;` → strip RFC 5987 prefix → strip quotes.

### Code quality

- **SonarCloud open issues: 0** (51 auto-fixed by the refactors above,
  8 reviewed and accepted with documented justification — see
  `ROADMAP.md` for the per-rule rationale).
- **Coverage**: 98.9% LINE / 91.9% BRANCH / 100% METHOD (local JaCoCo).
  SonarCloud aggregated: 97.5%.

[1.0.3]: https://github.com/calcifux/remote-download-java/releases/tag/v1.0.3

## [1.0.2] — 2026-05-11

### Added

- **JaCoCo coverage** wired into the parent pom — every module produces
  `target/jacoco.exec` and `target/site/jacoco/index.html` on `mvn verify`.
- **Quality gate** enforcing 95% LINE / 70% BRANCH per BUNDLE. Modules
  without tests skip the gate automatically; the day they get their first
  test, the gate kicks in.
- `lombok.config` at the project root with
  `lombok.addLombokGeneratedAnnotation = true` so JaCoCo filters
  Lombok-produced bytecode (`@Getter`, `@Builder`, etc.) out of coverage.
- **45 new tests** raising the suite to **95 tests verde** across `core`
  (43), `apache` (20), `spring` (17) and `quarkus` (15) modules.

### Changed

- `remote-download-quarkus`: `encodeFilename` rewritten to delegate to
  `URLEncoder.encode(...)` instead of looping byte-by-byte over the
  RFC 5987 unreserved set. Same output, simpler code, 12 fewer synthetic
  branches in the coverage report.

### Fixed

- `remote-download-apache`: `HttpHeaderUtils.parseFilename` was stripping
  surrounding quotes BEFORE cutting at the parameter separator `;`. The
  bug surfaced on headers like
  `Content-Disposition: attachment; filename="x.pdf"; size=42`, which
  returned `"x.pdf"` (with literal quotes) instead of `x.pdf`. The order
  of operations is now: cut at `;` → strip RFC 5987 prefix → strip quotes.

### Coverage baseline

```
Module                Line %   Branch %   Method %
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
