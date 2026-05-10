# remote-download-java — Spring Boot demo

Minimal Spring Boot 3 application that consumes `remote-download-java` directly from JitPack and proxies a remote PDF to the browser without persisting it on the server.

## Run

```bash
cd examples/spring-boot-demo
mvn spring-boot:run
```

The first run downloads the JitPack artifacts (`remote-download-core` + `remote-download-spring`) and the Spring Boot dependencies.

## Endpoints

| Endpoint | What it does |
| --- | --- |
| `GET /download` | Forces a download with `Content-Disposition: attachment` |
| `GET /preview` | Renders the PDF inline in the browser when possible |
| `GET /health` | Plain health check |

Open in a browser:

- <http://localhost:8080/download>
- <http://localhost:8080/preview>
- <http://localhost:8080/health>

## What this demonstrates

- Consuming the library from JitPack with the proper groupId
  `com.github.calcifux.remote-download-java`
- The Spring Boot starter auto-configures the `RemoteDownloadService` bean from
  `application.yml` properties
- Both the static factory (`Downloads.attachment(...)`) and the injected bean
  (`RemoteDownloadService.inline(...)`) coexist and produce the same kind of
  `ResponseEntity<StreamingResponseBody>`

## Try with another origin

To stream from S3 instead of a public URL, uncomment the `remote-download-s3`
dependency in [`pom.xml`](pom.xml) and replace the controller body with:

```java
DownloadOrigin source = S3Origin.builder()
    .bucket("my-bucket")
    .key("contracts/2026/abc.pdf")
    .region("us-east-1")
    .build();
return Downloads.attachment(source, "abc.pdf");
```

Provide AWS credentials via the standard chain (env vars, `~/.aws/credentials`
or an IAM role).
