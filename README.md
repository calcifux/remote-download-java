# remote-download

[![build](https://github.com/calcifux/remote-download-java/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/calcifux/remote-download-java/actions/workflows/build.yml)
[![JitPack](https://jitpack.io/v/calcifux/remote-download-java.svg)](https://jitpack.io/#calcifux/remote-download-java)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=calcifux_remote-download-java&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=calcifux_remote-download-java)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=calcifux_remote-download-java&metric=coverage)](https://sonarcloud.io/summary/new_code?id=calcifux_remote-download-java)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

> Read this in other languages: [Español](README-es.md)

> **Pipe bytes** from a remote source (authenticated HTTP, S3, Azure Blob, GCS, SFTP, FTP, ...) straight into your backend's HTTP response, **never touching disk**, so the user's browser triggers the download.
>
> Inspired by Laravel's `response()->streamDownload(...)` and the `Guzzle::request($url, ['stream' => true])` pattern in PHP.

Works in **Spring Boot**, **Quarkus**, JAX-RS, plain Servlet, AWS Lambda, CLI scripts — anywhere you can write bytes to an `OutputStream`.

## What this is NOT

This is **not** a library to *save remote files to the server's disk* and serve them later. While you can technically point `writeTo(...)` at a `FileOutputStream`, that is **not the central use case**.

The central use case is:

```
[ authenticated remote origin ]  →  [ your backend (memory only, no disk) ]  →  [ end-user browser ]
       (S3 / Azure / HTTP API ...)            8 KiB chunks                         direct download
```

Your backend acts as an **authenticated proxy**: the client never sees nor reaches the real origin, the credentials never leave the server, and bytes are forwarded to the client as they arrive — without piling up in RAM or in temp files.

---

## Table of contents

- [Why it exists](#why-it-exists)
- [Laravel parallels](#laravel-parallels)
- [Modules](#modules)
- [Quick start](#quick-start)
- [Concepts](#concepts)
- [Universal API](#universal-api)
- [Origins (DownloadOrigin)](#origins-downloadorigin)
- [The `response()->download()` pattern in Java](#the-response-download-pattern-in-java)
- [Framework integration](#framework-integration)
- [Configuration via properties](#configuration-via-properties)
- [Custom DownloadOrigin](#custom-downloadorigin)
- [License](#license)

---

## Why it exists

In PHP / Laravel, returning a remote file to the user's browser is a one-liner:

```php
return response()->streamDownload(fn () => /* read from remote origin */, 'report.pdf');
```

That is exactly what this library does: your controller acts as an **authenticated proxy** between the origin (which the browser must not know) and the end client. Bytes flow through your backend but never touch disk — they are forwarded in chunks as they arrive.

In Java each framework ships its own recipe: `StreamingResponseBody` in Spring, `StreamingOutput` in JAX-RS / Quarkus, `HttpServletResponse.getOutputStream()` in plain Servlet. Connecting to authenticated origins (HTTP with bearer tokens, S3, Azure Blob, GCS) requires distinct boilerplate per SDK.

`remote-download` solves both halves:

1. **A universal API** — `RemoteDownload.from(source).writeTo(out)` — that works in any framework. The `out` argument is almost always the HTTP response's `OutputStream`, not a file.
2. **Unified origins** for HTTP, enterprise HTTP (NTLM, retries, proxy auth), S3, Azure Blob, GCS, SFTP and FTP, all behind the same interface.

Net effect: a single line forwards a PDF that lives in S3 to the browser from a Spring or Quarkus controller, without the file touching the server's disk and without the client ever seeing the origin's credentials.

## How it works

```
┌────────────────────┐     credentials and headers
│  Remote origin     │     handled by remote-download
│  (S3 / Azure /     │ ◄─────────────────────────────────┐
│   GCS / HTTP ...)  │                                   │
└─────────┬──────────┘                                   │
          │  bytes in chunks                             │
          │  (default 8 KiB)                             │
          ▼                                              │
┌────────────────────────────────────────────────────────┴──┐
│  Your backend (Spring / Quarkus / Servlet / Lambda / ...) │
│                                                           │
│   RemoteDownload.from(source).writeTo(response.outputStream)│
│                                                           │
│   - No filesystem persistence                             │
│   - No full-body buffering in RAM                         │
│   - Per-chunk flush: client receives bytes immediately    │
└─────────┬─────────────────────────────────────────────────┘
          │  HTTP response body
          │  Content-Disposition: attachment; filename="..."
          ▼
┌────────────────────┐
│  End-user          │
│  browser           │   triggers download / preview
└────────────────────┘
```

Key properties:

- **The backend never persists the file.** It reads from the origin and writes to the response simultaneously.
- **The client does not see the origin.** It only sees your public URL. Tokens, keys and SAS values stay on the server.
- **True streaming.** A 500 MB file does not move your RAM beyond the chunk size (8 KiB by default). The browser starts receiving bytes immediately, not after the whole payload reaches your server.
- **Same idea as Laravel's `response()->streamDownload(...)`**, made universal across Java frameworks and pre-packaged with providers for the major clouds.

---

## Laravel parallels

Side by side. The Java controller on the right is **functionally identical** to the Laravel one.

### 1. Streaming an authenticated remote file

```php
// Laravel
public function download(string $id, StreamRemoteDownload $stream)
{
    $url = "https://files.example.com/contracts/{$id}.pdf";
    return $stream($url, "{$id}.pdf");
}
```

```java
// Spring Boot
@GetMapping("/download/{id}")
public ResponseEntity<StreamingResponseBody> download(@PathVariable String id) {
    DownloadOrigin src = HttpOrigin.url("https://files.example.com/contracts/" + id + ".pdf").build();
    return Downloads.attachment(src, id + ".pdf");
}
```

```java
// Quarkus
@GET @Path("/download/{id}")
public Response download(@PathParam("id") String id) {
    DownloadOrigin src = HttpOrigin.url("https://files.example.com/contracts/" + id + ".pdf").build();
    return Downloads.attachment(src, id + ".pdf");
}
```

### 2. With Bearer authentication

```php
// Laravel + Guzzle
$client = new Client(['headers' => ['Authorization' => 'Bearer '.$token]]);
return $stream($url, $name);   // your StreamRemoteDownload expects an already-authenticated URL
```

```java
// Spring / Quarkus — the source carries auth
DownloadOrigin src = HttpOrigin.url(url).bearer(token).build();
return Downloads.attachment(src, name);
```

### 3. From Amazon S3

```php
// Laravel + flysystem
return Storage::disk('s3')->download("contracts/{$id}.pdf", "{$id}.pdf");
```

```java
// Spring / Quarkus
DownloadOrigin src = S3Origin.builder()
    .bucket("my-bucket")
    .key("contracts/" + id + ".pdf")
    .region("us-east-1")
    .build();
return Downloads.attachment(src, id + ".pdf");
```

### 4. Inline preview in the browser

```php
// Laravel
return response()->file($path);
```

```java
// Spring / Quarkus
return Downloads.inline(src, "preview.pdf");
```

---

## Modules

| Module | Description | Added dependencies |
| --- | --- | --- |
| **`remote-download-core`** | Universal API + HTTP via JDK HttpClient (Basic / Bearer / headers) + progress + checksum | Just `slf4j-api` |
| `remote-download-apache` | Enterprise HTTP: retries with backoff, NTLM, Kerberos, proxy auth, granular timeouts | Apache HttpClient 5 |
| `remote-download-s3` | Amazon S3 with the default credentials chain (env, profile, IAM) | AWS SDK v2 (`s3`) |
| `remote-download-azure` | Azure Blob (Connection String, SAS) | `azure-storage-blob` |
| `remote-download-gcs` | Google Cloud Storage (ADC, service account JSON, Workload Identity) | `google-cloud-storage` |
| `remote-download-sftp` | SFTP with password or private-key authentication | Apache Mina SSHD |
| `remote-download-ftp` | FTP / FTPS with passive mode and binary transfers | Apache Commons Net |
| `remote-download-spring` | Spring Boot starter: auto-config + `ConfigurationProperties` + static factory | `spring-boot-autoconfigure` + `spring-webmvc` |
| `remote-download-quarkus` | Quarkus / JAX-RS / CDI: `@ApplicationScoped` bean + JAX-RS helpers + static factory | Jakarta CDI + JAX-RS + MicroProfile Config |

`core` is mandatory. All other modules are optional and additive.

---

## Quick start

### 1. Add the modules you need

For a Spring Boot app downloading public files:

```xml
<dependency>
  <groupId>com.github.calcifux.remote-download-java</groupId>
  <artifactId>remote-download-core</artifactId>
  <version>v1.0.3</version>
</dependency>
<dependency>
  <groupId>com.github.calcifux.remote-download-java</groupId>
  <artifactId>remote-download-spring</artifactId>
  <version>v1.0.3</version>
</dependency>
```

### 2. Use it from a controller

```java
@RestController
public class FilesController {

    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> download() {
        return Downloads.attachment(
            HttpOrigin.url("https://files.example.com/report.pdf").build(),
            "report.pdf"
        );
    }
}
```

That is all. Spring auto-configuration handles the rest.

---

## Concepts

### `DownloadOrigin`

Central interface. Anything that can produce remote bytes implements it: an HTTP URL, an S3 object, an Azure blob, a GCS object, or your own custom origin.

```java
public interface DownloadOrigin {
    RemoteContent open() throws IOException;
}
```

### `RemoteContent`

`AutoCloseable` wrapper exposing the live `InputStream` plus metadata (content type, length, filename). Use it with try-with-resources to release the underlying connection.

### `RemoteDownload`

Static entry point with two factory methods:

```java
RemoteDownload.from(String url)            // shortcut: public HttpOrigin
RemoteDownload.from(DownloadOrigin src)    // any source
```

Returns a `RemoteDownloadRequest` exposing three ways to consume the content.

---

## Universal API

```java
// 1. Common form: copies everything to the destination OutputStream and releases resources
WriteResult result = RemoteDownload.from(src).writeTo(outputStream);
log.info("transferred {} bytes in {}", result.getBytesTransferred(), result.getDuration());

// 2. Custom chunk size
RemoteDownload.from(src).chunkSize(16 * 1024).writeTo(outputStream);

// 3. When you need metadata + stream
try (RemoteContent c = RemoteDownload.from(src).fetch()) {
    System.out.println("size:        " + c.contentLength().orElse(-1L));
    System.out.println("contentType: " + c.contentType().orElse("?"));
    System.out.println("filename:    " + c.filename().orElse("?"));
    c.getInputStream().transferTo(outputStream);
}

// 4. Just the InputStream to feed another library
try (InputStream in = RemoteDownload.from(src).asInputStream()) {
    PDDocument doc = PDDocument.load(in);
}
```

### Progress

```java
RemoteDownload.from(src)
    .onProgress((bytesRead, totalBytes) -> {
        long pct = totalBytes != null ? (bytesRead * 100L / totalBytes) : -1;
        log.info("downloaded {} / {} bytes ({}%)", bytesRead, totalBytes, pct);
    })
    .writeTo(outputStream);
```

`totalBytes` may be `null` when the origin does not advertise `Content-Length` (some HTTP endpoints with `Transfer-Encoding: chunked`, certain SFTP servers).

### Checksum

```java
WriteResult result = RemoteDownload.from(src)
    .checksum("SHA-256")
    .writeTo(outputStream);

result.checksum().ifPresent(hex ->
    log.info("file integrity hash: {}", hex)
);
```

The digest is computed **as bytes flow** through the pipe — no extra pass over the data. Valid algorithms are anything `java.security.MessageDigest` recognises: `MD5`, `SHA-1`, `SHA-256`, `SHA-512`, etc.

### Combine progress + checksum + custom chunk

```java
WriteResult result = RemoteDownload.from(src)
    .chunkSize(16 * 1024)
    .onProgress((read, total) -> metrics.gauge("dl.bytes", read))
    .checksum("SHA-256")
    .writeTo(out);

audit.record(filename,
    result.getBytesTransferred(),
    result.checksum().orElse(null),
    result.getDuration());
```

---

## Origins (DownloadOrigin)

### Plain HTTP (`remote-download-core`)

```java
// Public URL
HttpOrigin.url("https://cdn.example.com/file.pdf").build();

// Bearer token
HttpOrigin.url(url).bearer("eyJhbGc...").build();

// Basic auth
HttpOrigin.url(url).basicAuth("user", "pass").build();

// Custom headers + timeouts
HttpOrigin.url(url)
    .header("X-Tenant", "acme")
    .header("Accept-Language", "en-US")
    .connectTimeout(Duration.ofSeconds(10))
    .requestTimeout(Duration.ofMinutes(2))
    .build();
```

### Enterprise HTTP (`remote-download-apache`)

```java
ApacheHttpOrigin.url(url)
    .ntlm("CORP", "user", "pass")        // SharePoint, Windows intranets
    .proxy("proxy.corp", 8080)
    .proxyAuth("proxyUser", "proxyPass")
    .retries(3)                          // backoff via DefaultHttpRequestRetryStrategy
    .retryInterval(Duration.ofSeconds(2))
    .responseTimeout(Duration.ofMinutes(5))
    .build();
```

### Amazon S3 (`remote-download-s3`)

```java
// Default credentials chain (env, ~/.aws/credentials, IAM role)
S3Origin.builder()
    .bucket("my-bucket")
    .key("contracts/2026/abc.pdf")
    .region("us-east-1")
    .build();

// Explicit credentials
S3Origin.builder()
    .bucket("...").key("...").region("us-west-2")
    .credentials(accessKey, secretKey)
    .build();

// MinIO / LocalStack / S3-compatible services
S3Origin.builder()
    .bucket("...").key("...").region("us-east-1")
    .endpoint("http://localhost:9000")
    .credentials("minioadmin", "minioadmin")
    .build();
```

### Azure Blob (`remote-download-azure`)

```java
// Connection String (most common path)
AzureBlobOrigin.builder()
    .container("my-container")
    .blob("contracts/2026/abc.pdf")
    .connectionString(System.getenv("AZURE_STORAGE_CONNECTION_STRING"))
    .build();

// Endpoint + SAS token
AzureBlobOrigin.builder()
    .container("...").blob("...")
    .endpoint("https://myaccount.blob.core.windows.net")
    .sasToken("?sv=2024-...&sig=...")
    .build();
```

### Google Cloud Storage (`remote-download-gcs`)

```java
// Application Default Credentials (gcloud auth, GKE, Cloud Run)
GcsOrigin.builder()
    .bucket("my-bucket")
    .object("contracts/2026/abc.pdf")
    .projectId("my-project")
    .build();

// Service-account JSON from the classpath
GcsOrigin.builder()
    .bucket("...").object("...")
    .credentialsPath("classpath:gcp/sa-key.json")
    .build();

// Service-account JSON from the filesystem
GcsOrigin.builder()
    .bucket("...").object("...")
    .credentialsPath("/etc/keys/sa-key.json")
    .build();
```

### SFTP (`remote-download-sftp`)

```java
// Password
SftpOrigin.builder()
    .host("sftp.example.com")
    .user("svc-downloads")
    .password(System.getenv("SFTP_PASS"))
    .path("/uploads/contracts/abc.pdf")
    .build();

// Private key (PEM file on the filesystem)
SftpOrigin.builder()
    .host("sftp.example.com")
    .user("svc-downloads")
    .privateKey("/etc/keys/sftp_id_rsa")
    .path("/uploads/contracts/abc.pdf")
    .connectTimeout(Duration.ofSeconds(15))
    .authTimeout(Duration.ofSeconds(15))
    .build();
```

### FTP / FTPS (`remote-download-ftp`)

```java
// Plain FTP
FtpOrigin.builder()
    .host("ftp.example.com")
    .user("anonymous")
    .password("guest@example.com")
    .path("/pub/file.zip")
    .build();

// FTPS (TLS over the control channel)
FtpOrigin.builder()
    .host("ftps.example.com")
    .secure(true)
    .user("svc")
    .password(System.getenv("FTP_PASS"))
    .path("/private/report.pdf")
    .dataTimeout(Duration.ofMinutes(10))
    .build();
```

---

## The `response()->download()` pattern in Java

This is the section you are likely most interested in: how to idiomatically replicate Laravel's `response()->download()`, `response()->streamDownload()` and `response()->file()` helpers.

### Quick mappings

| Laravel | Spring (static factory) | Quarkus (static factory) |
| --- | --- | --- |
| `response()->download($path, $name)` | `Downloads.attachment(src, name)` | `Downloads.attachment(src, name)` |
| `response()->streamDownload($cb, $name)` | `Downloads.attachment(src, name)` | `Downloads.attachment(src, name)` |
| `response()->file($path)` | `Downloads.inline(src, name)` | `Downloads.inline(src, name)` |
| `response()->stream($cb)` | `Downloads.stream(src)` | `Downloads.stream(src)` |

> `Downloads` is a **utility class with static methods** (just like `ResponseEntity.ok()` or `List.of()`). No global state, no initialization — every call builds a brand new response. If you need to drive the chunk size from `application.yml`, inject the `RemoteDownloadService` bean (Spring) or `RemoteDownloadJaxRsService` bean (Quarkus); both coexist with the utility class.

### Three response modes

#### `attachment(source, filename)` — force download

```java
return Downloads.attachment(src, "invoice-2026-001.pdf");
```

Emits the header:

```
Content-Disposition: attachment; filename="invoice-2026-001.pdf"; filename*=UTF-8''invoice-2026-001.pdf
```

The browser always downloads the file — never tries to render it inline.

#### `inline(source, filename)` — preview in the browser

```java
return Downloads.inline(src, "preview.pdf");
```

```
Content-Disposition: inline; filename="preview.pdf"; filename*=UTF-8''preview.pdf
```

The browser renders the PDF / image / video when it has a native plugin; otherwise it falls back to a download.

#### `stream(source)` — no disposition

```java
return Downloads.stream(src);
```

Does not add a `Content-Disposition` header. Useful when forwarding bytes between internal microservices or when the client decides framing on its own.

### Filenames with non-ASCII characters

`remote-download` always emits **two forms** of the filename — the legacy quoted form and the `RFC 5987` `filename*=UTF-8''` form — so both modern and legacy browsers handle them correctly:

```java
return Downloads.attachment(src, "Quarterly report – 2026.pdf");
```

```
Content-Disposition: attachment;
    filename="Quarterly report – 2026.pdf";
    filename*=UTF-8''Quarterly%20report%20%E2%80%93%202026.pdf
```

### Static factory vs injected bean

Both forms are valid; pick whichever fits your taste:

```java
// 1) Static factory (Laravel-style ergonomics, hard-coded defaults)
@GetMapping("/download/{id}")
public ResponseEntity<StreamingResponseBody> download(@PathVariable String id) {
    return Downloads.attachment(buildSource(id), id + ".pdf");
}

// 2) Injected bean (idiomatic Spring, chunk size from application.yml)
@RequiredArgsConstructor
@RestController
public class FilesController {

    private final RemoteDownloadService streamer;

    @GetMapping("/download/{id}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String id) {
        return streamer.attachment(buildSource(id), id + ".pdf");
    }
}
```

> To override the chunk size with the static factory pass it as the third
> argument: `Downloads.attachment(src, name, 16 * 1024)`.

### Extra headers

If you need headers beyond `Content-Disposition`, take the response from the static factory and decorate it:

```java
ResponseEntity<StreamingResponseBody> resp = Downloads.attachment(src, name);
return ResponseEntity.ok()
    .headers(resp.getHeaders())
    .header("X-Doc-Version", version)
    .header("Cache-Control", "private, max-age=0, no-cache")
    .contentType(MediaType.APPLICATION_PDF)
    .body(resp.getBody());
```

### End-to-end example: signed PDF from S3 with audit logging

```java
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/contracts")
public class ContractDownloadController {

    private final ContractService contracts;
    private final AuditService audit;

    @GetMapping("/{id}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String id,
                                                          @AuthenticationPrincipal User user) {
        Contract c = contracts.findOrThrow(id);
        contracts.assertCanRead(user, c);
        audit.recordDownload(user, c);

        DownloadOrigin src = S3Origin.builder()
            .bucket(c.getBucket())
            .key(c.getObjectKey())
            .region(c.getRegion())
            .build();

        return Downloads.attachment(src, c.getDownloadFilename());
    }
}
```

---

## Framework integration

### Spring Boot (with the starter)

```xml
<dependency>
  <groupId>com.github.calcifux.remote-download-java</groupId>
  <artifactId>remote-download-spring</artifactId>
  <version>v1.0.3</version>
</dependency>
```

```java
@RestController
public class FilesController {

    // 1. Static factory (utility class, no injection)
    @GetMapping("/download/{id}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String id) {
        return Downloads.attachment(buildSource(id), id + ".pdf");
    }

    // 2. Injected bean (chunk size + disposition from application.yml)
    private final RemoteDownloadService streamer;
    public FilesController(RemoteDownloadService s) { this.streamer = s; }

    @GetMapping("/preview/{id}")
    public ResponseEntity<StreamingResponseBody> preview(@PathVariable String id) {
        return streamer.inline(buildSource(id), "preview.pdf");
    }
}
```

### Quarkus / JAX-RS

```xml
<dependency>
  <groupId>com.github.calcifux.remote-download-java</groupId>
  <artifactId>remote-download-quarkus</artifactId>
  <version>v1.0.3</version>
</dependency>
```

```java
@Path("/files")
public class FilesResource {

    // 1. Static factory (utility class, no injection)
    @GET @Path("/{id}")
    public Response download(@PathParam("id") String id) {
        return Downloads.attachment(buildSource(id), id + ".pdf");
    }

    // 2. Injected bean (chunk size + disposition from application.properties)
    @Inject RemoteDownloadJaxRsService streamer;

    @GET @Path("/preview/{id}")
    public Response preview(@PathParam("id") String id) {
        return streamer.inline(buildSource(id), "preview.pdf");
    }
}
```

### Plain JAX-RS (Helidon, OpenLiberty, ...)

The `remote-download-quarkus` module only depends on Jakarta CDI + JAX-RS + MicroProfile Config, so it works unchanged outside of Quarkus.

### Plain Servlet

```java
@WebServlet("/download")
public class DownloadServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        DownloadOrigin src = HttpOrigin.url(req.getParameter("url")).build();

        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment; filename=\"file.pdf\"");

        RemoteDownload.from(src).writeTo(resp.getOutputStream());
    }
}
```

### Secondary use cases (not the central one)

> Although the library exists to **proxy bytes to the browser**, `DownloadOrigin` produces a real `InputStream`, so it can also be consumed in other ways when needed.

**Save a file to disk from a batch job / CLI / Lambda:**

```java
public static void main(String[] args) throws IOException {
    DownloadOrigin src = HttpOrigin.url(args[0]).build();
    try (FileOutputStream fos = new FileOutputStream(args[1])) {
        WriteResult r = RemoteDownload.from(src).writeTo(fos);
        System.out.printf("%d bytes -> %s%n", r.getBytesTransferred(), args[1]);
    }
}
```

**Feed another library with the `InputStream`** (without persisting to disk):

```java
try (InputStream in = RemoteDownload.from(src).asInputStream()) {
    PDDocument pdf = PDDocument.load(in);
    // ...
}

try (InputStream in = RemoteDownload.from(src).asInputStream()) {
    Workbook xlsx = new XSSFWorkbook(in);
    // ...
}
```

> In these cases you lose the "the client never sees the origin" property — because the client here is your own process. For browser-download scenarios, always use `writeTo(response.getOutputStream())` or the static factory.

---

## Configuration via properties

### Spring Boot — `application.yml`

```yaml
remote-download:
  enabled: true               # default true
  chunk-size: 8192            # bytes; default 8 KiB
  default-disposition: attachment
```

### Quarkus — `application.properties`

```properties
remote-download.chunk-size=8192
remote-download.default-disposition=attachment
```

### Available properties

| Property | Default | Description |
| --- | --- | --- |
| `remote-download.enabled` | `true` | Toggles the auto-configuration (Spring only) |
| `remote-download.chunk-size` | `8192` | Buffer size used when copying content, in bytes |
| `remote-download.default-disposition` | `attachment` | `attachment` or `inline` when not specified explicitly |

---

## Custom DownloadOrigin

Need Dropbox, SharePoint, an exotic REST API? Implement the interface:

```java
public class DropboxOrigin implements DownloadOrigin {

    private final DbxClientV2 client;
    private final String path;

    @Override
    public RemoteContent open() throws IOException {
        try {
            DbxDownloader<FileMetadata> downloader = client.files().download(path);
            return RemoteContent.builder()
                .inputStream(downloader.getInputStream())
                .filename(downloader.getResult().getName())
                .contentLength(downloader.getResult().getSize())
                .onClose(() -> {
                    try { downloader.close(); } catch (Exception ignored) {}
                })
                .build();
        } catch (DbxException e) {
            throw new IOException("Dropbox download failed: " + path, e);
        }
    }
}
```

Then use it the same way as built-in origins:

```java
DownloadOrigin src = new DropboxOrigin(dbxClient, "/contracts/abc.pdf");
return Downloads.attachment(src, "abc.pdf");
```

---

## Repository layout

```
remote-download-java/
├── pom.xml                      (parent, packaging=pom)
├── README.md
├── README-es.md
├── .gitignore
│
├── remote-download-core/
│   └── src/main/java/com/github/calcifux/remotedownload/
│       ├── RemoteDownload.java
│       ├── RemoteDownloadRequest.java
│       ├── RemoteDownloadException.java
│       ├── DownloadOrigin.java
│       ├── RemoteContent.java
│       ├── ProgressListener.java
│       ├── WriteResult.java
│       ├── StreamWriter.java
│       └── http/HttpOrigin.java
│
├── remote-download-apache/
│   └── ...apache/ApacheHttpOrigin.java
│
├── remote-download-s3/
│   └── ...s3/S3Origin.java
│
├── remote-download-azure/
│   └── ...azure/AzureBlobOrigin.java
│
├── remote-download-gcs/
│   └── ...gcs/GcsOrigin.java
│
├── remote-download-sftp/
│   └── ...sftp/SftpOrigin.java
│
├── remote-download-ftp/
│   └── ...ftp/FtpOrigin.java
│
├── remote-download-spring/
│   ├── ...spring/Downloads.java                                (static factory)
│   ├── ...spring/config/RemoteDownloadAutoConfiguration.java
│   ├── ...spring/config/RemoteDownloadProperties.java
│   ├── ...spring/core/RemoteDownloadService.java               (injectable bean)
│   └── resources/META-INF/spring/...AutoConfiguration.imports
│
└── remote-download-quarkus/
    ├── ...quarkus/Downloads.java                               (static factory)
    └── ...quarkus/core/RemoteDownloadJaxRsService.java         (CDI bean)
```

---

## License

MIT — use, copy, modify.
