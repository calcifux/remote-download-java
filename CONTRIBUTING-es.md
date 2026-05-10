# Cómo contribuir a remote-download-java

> Léelo en otro idioma: [English](CONTRIBUTING.md)

Gracias por tu interés en contribuir. Este documento explica cómo levantar el proyecto local, las convenciones que seguimos y cómo enviar un Pull Request con la mejor probabilidad de ser mergeado.

## Quick start

```bash
git clone https://github.com/calcifux/remote-download-java.git
cd remote-download-java
mvn clean install
```

Si `mvn clean install` termina con **BUILD SUCCESS**, ya estás listo para desarrollar.

## Requisitos

- **Java 21** — la librería compila con `--release 21` (records, pattern matching, virtual threads)
- **Maven 3.9+** — versiones más viejas funcionan, pero el parent pom asume plugins recientes
- **Git** con auth SSH o HTTPS contra GitHub

## Estructura del repositorio

```
remote-download-java/                        (parent reactor)
├── remote-download-core                     ← API universal + JDK HttpClient
├── remote-download-apache                   ← HTTP enterprise
├── remote-download-s3                       ← Amazon S3
├── remote-download-azure                    ← Azure Blob
├── remote-download-gcs                      ← Google Cloud Storage
├── remote-download-sftp                     ← SFTP
├── remote-download-ftp                      ← FTP / FTPS
├── remote-download-spring                   ← Spring Boot starter
├── remote-download-quarkus                  ← Quarkus / JAX-RS / CDI
└── examples/spring-boot-demo                ← demo consumidor (NO está en el reactor)
```

La app de ejemplo bajo `examples/` **no** forma parte del reactor del parent — consume los artefactos publicados en JitPack de forma independiente.

## Branches y commits

- `main` siempre está verde y listo para release. No pushees directo; abre un PR.
- Usa **feature branches**: `feat/range-requests`, `fix/sftp-timeout`, `docs/install-guide`.
- Sigue [Conventional Commits](https://www.conventionalcommits.org/) en el subject:
  - `feat:` nueva funcionalidad
  - `fix:` corrección de bug
  - `docs:` solo documentación
  - `refactor:` sin cambio de comportamiento
  - `test:` agregar o refactorizar tests
  - `ci:` CI / build tooling
  - `chore:` mantenimiento

## Estilo de código

- Idiomatic Java 21 (records, pattern matching, `var` para variables locales cuando el tipo es obvio)
- **Lombok** para boilerplate (`@Getter`, `@Slf4j`, `@RequiredArgsConstructor`)
- Javadocs en cada clase y método público, en **inglés**, tono enterprise
- Logs vía SLF4J con prefijo `[NombreClase]` en INFO/DEBUG, texto en inglés
- Imports en orden alfabético (default de IntelliJ)

## Agregar un nuevo origin

Para agregar una nueva implementación de `DownloadOrigin`:

1. Crea un nuevo módulo `remote-download-{nombre}` siguiendo el layout de `remote-download-sftp`
2. Agrégalo al parent pom bajo `<modules>` y a `<dependencyManagement>` si introduces una dep nueva
3. Implementa `DownloadOrigin` con un `Builder` fluido
4. Asegúrate que `RemoteContent.onClose` libere todos los recursos subyacentes
5. Actualiza la tabla de *Modules* del README
6. Abre un PR

## Correr el build igual que CI

```bash
mvn -B clean install -DskipTests --no-transfer-progress
```

Es el mismo comando que corre `.github/workflows/build.yml`.

## Checklist del Pull Request

- [ ] `mvn clean install` pasa local
- [ ] Los archivos nuevos tienen Javadoc adecuado + tag `@since`
- [ ] El subject del commit sigue Conventional Commits
- [ ] Sin datos personales, secretos ni credenciales en código o mensajes
- [ ] Si cambió la API pública, el README se actualizó
- [ ] Si se agregó un nuevo módulo, el parent pom y la tabla *Modules* se actualizaron

## Reportar bugs

Abre un [bug report](https://github.com/calcifux/remote-download-java/issues/new?template=bug_report.yml) con:

- El módulo afectado (`core`, `s3`, `sftp`, ...)
- La versión que estás usando (commit hash o `vX.Y.Z`)
- Una reproducción mínima (builder del origin + la llamada que falla)
- Comportamiento esperado vs. real
- Stack trace si lo tienes

## Sugerir features

Abre un [feature request](https://github.com/calcifux/remote-download-java/issues/new?template=feature_request.yml) describiendo:

- El caso de uso en un párrafo
- El workaround actual (si lo hay)
- Un sketch de la API propuesta

## Licencia

Al contribuir aceptas que tu contribución se licencia bajo la [licencia MIT](LICENSE).
