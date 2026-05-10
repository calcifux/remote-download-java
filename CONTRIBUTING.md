# Contributing to remote-download-java

> Read this in other languages: [Español](CONTRIBUTING-es.md)

Thanks for your interest in contributing! This document explains how to set up the project locally, the conventions we follow, and how to send a Pull Request that has the best chance of being merged.

## Quick start

```bash
git clone https://github.com/calcifux/remote-download-java.git
cd remote-download-java
mvn clean install
```

If `mvn clean install` ends with **BUILD SUCCESS** you are ready to develop.

## Requirements

- **Java 21** — the library targets `--release 21` (records, pattern matching, virtual threads)
- **Maven 3.9+** — older Maven works but the parent pom assumes recent plugins
- **Git** with SSH or HTTPS auth to GitHub

## Repository layout

```
remote-download-java/                        (parent reactor)
├── remote-download-core                     ← universal API + JDK HttpClient
├── remote-download-apache                   ← HTTP enterprise
├── remote-download-s3                       ← Amazon S3
├── remote-download-azure                    ← Azure Blob
├── remote-download-gcs                      ← Google Cloud Storage
├── remote-download-sftp                     ← SFTP
├── remote-download-ftp                      ← FTP / FTPS
├── remote-download-spring                   ← Spring Boot starter
├── remote-download-quarkus                  ← Quarkus / JAX-RS / CDI
└── examples/spring-boot-demo                ← consumer demo (NOT in reactor)
```

The example app under `examples/` is **not** part of the parent reactor — it consumes the published JitPack artifacts independently.

## Branching and commits

- `main` is always green and deployable. Don't push directly; open a PR.
- Use **feature branches**: `feat/range-requests`, `fix/sftp-timeout`, `docs/install-guide`.
- Follow [Conventional Commits](https://www.conventionalcommits.org/) for the subject line:
  - `feat:` new feature
  - `fix:` bug fix
  - `docs:` documentation only
  - `refactor:` no behavior change
  - `test:` adding or refactoring tests
  - `ci:` CI / build tooling
  - `chore:` housekeeping

## Code style

- Java 21 idioms (records, pattern matching, `var` for local vars when the type is obvious)
- **Lombok** for boilerplate (`@Getter`, `@Slf4j`, `@RequiredArgsConstructor`)
- Javadocs on every public class and public method, in **English**, enterprise tone
- Logs via SLF4J with a `[ClassName]` prefix at INFO/DEBUG, English text
- Imports ordered alphabetically (IntelliJ default)

## Adding a new origin

To add a new `DownloadOrigin` implementation:

1. Create a new module `remote-download-{name}` mirroring the layout of `remote-download-sftp`
2. Add it to the parent pom under `<modules>` and to `<dependencyManagement>` if any new dep is introduced
3. Implement `DownloadOrigin` with a fluent `Builder`
4. Make sure `RemoteContent.onClose` releases every underlying resource
5. Update the README *Modules* table
6. Open a PR

## Running the build the same way as CI

```bash
mvn -B clean install -DskipTests --no-transfer-progress
```

Same command as `.github/workflows/build.yml`.

## Pull Request checklist

- [ ] `mvn clean install` passes locally
- [ ] New files have the proper Javadoc + `@since` tag
- [ ] Conventional Commit format in the subject
- [ ] No personal data, secrets, or credentials in code or commit messages
- [ ] If a public API changed, the README was updated accordingly
- [ ] If a new module was added, the parent pom and the README *Modules* table were updated

## Reporting bugs

Open a [bug report](https://github.com/calcifux/remote-download-java/issues/new?template=bug_report.yml) with:

- The module affected (`core`, `s3`, `sftp`, ...)
- The version you are using (commit hash or `vX.Y.Z`)
- A minimal reproduction (origin builder + the call that fails)
- Expected vs. actual behavior
- Stack trace if any

## Suggesting features

Open a [feature request](https://github.com/calcifux/remote-download-java/issues/new?template=feature_request.yml) and describe:

- The use case in one paragraph
- The current workaround (if any)
- A proposed API sketch

## License

By contributing you agree that your contributions are licensed under the [MIT License](LICENSE).
