# remote-download

[![build](https://github.com/calcifux/remote-download-java/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/calcifux/remote-download-java/actions/workflows/build.yml)
[![JitPack](https://jitpack.io/v/calcifux/remote-download-java.svg)](https://jitpack.io/#calcifux/remote-download-java)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

> Léelo en otro idioma: [English](README.md)

> **Pipe de bytes** desde una fuente remota (HTTP autenticado, S3, Azure Blob, GCS, ...) hacia el response HTTP de tu backend, **sin pasar por disco**, para que el navegador del usuario dispare la descarga.
>
> Inspirado en `response()->streamDownload(...)` de Laravel y en el patrón `Guzzle::request($url, ['stream' => true])` de PHP.

Funciona en **Spring Boot**, **Quarkus**, JAX-RS, Servlet plano, AWS Lambda, scripts CLI o cualquier otro lugar donde puedas escribir bytes a un `OutputStream`.

## Para qué NO es

Esta no es una librería para *bajar archivos al disco del servidor* y luego servirlos. Aunque técnicamente puedes apuntar el `writeTo(...)` a un `FileOutputStream` y guardar el archivo, **ese no es el caso de uso central**.

El caso de uso central es:

```
[ origen remoto autenticado ]  →  [ tu backend (memoria, sin disco) ]  →  [ navegador del usuario ]
       (S3 / Azure / API HTTP ...)         chunks de 8 KiB                     descarga directa
```

Tu backend actúa de **proxy autenticado**: el cliente nunca conoce ni alcanza el origen real, las credenciales nunca salen del servidor, y los bytes se reenvían al cliente conforme van llegando, sin acumularse en RAM ni en archivos temporales.

---

## Tabla de contenidos

- [Por qué existe](#por-qué-existe)
- [Equivalente con Laravel](#equivalente-con-laravel)
- [Módulos](#módulos)
- [Quick start](#quick-start)
- [Conceptos](#conceptos)
- [API universal](#api-universal)
- [Fuentes (DownloadOrigin)](#fuentes-downloadorigin)
- [Patrón `response()->download()` en Java](#patrón-response-download-en-java)
- [Uso por framework](#uso-por-framework)
- [Configuración por properties](#configuración-por-properties)
- [Custom DownloadOrigin](#custom-downloadorigin)
- [Licencia](#licencia)

---

## Por qué existe

En PHP/Laravel devolver un archivo remoto al navegador del usuario es una sola línea:

```php
return response()->streamDownload(fn () => /* lee del origen remoto */, 'reporte.pdf');
```

Eso hace exactamente lo que esta librería: tu controlador funciona como **proxy autenticado** entre el origen (que el navegador no debe conocer) y el cliente final. Los bytes pasan por tu backend pero nunca tocan disco — se reenvían en chunks conforme llegan.

En Java cada framework tiene su propia receta: `StreamingResponseBody` en Spring, `StreamingOutput` en JAX-RS/Quarkus, `HttpServletResponse.getOutputStream()` en Servlet plano. Además, conectarse a fuentes autenticadas (HTTP con bearer, S3, Azure Blob, GCS) requiere código boilerplate distinto en cada SDK.

`remote-download` resuelve las dos partes:

1. **Una API universal** — `RemoteDownload.from(source).writeTo(out)` — que funciona en cualquier framework. El `out` casi siempre es el `OutputStream` del response HTTP, no un archivo.
2. **Sources unificadas** para HTTP, HTTP enterprise (NTLM, retries, proxy), S3, Azure Blob y GCS, todas detrás de la misma interfaz.

Resultado: una línea para devolver al navegador un PDF que vive en S3 desde un controller Spring o Quarkus, sin que el archivo toque el disco del servidor y sin que el cliente vea jamás las credenciales del origen.

## Cómo funciona

```
┌────────────────────┐     credenciales y headers
│  Origen remoto     │     manejados por remote-download
│  (S3 / Azure /     │ ◄─────────────────────────────────┐
│   GCS / API ...)   │                                   │
└─────────┬──────────┘                                   │
          │  bytes en chunks                             │
          │  (default 8 KiB)                             │
          ▼                                              │
┌────────────────────────────────────────────────────────┴──┐
│  Tu backend (Spring / Quarkus / Servlet / Lambda / ...)   │
│                                                           │
│   RemoteDownload.from(source).writeTo(response.outputStream)│
│                                                           │
│   - Sin guardar a disco                                   │
│   - Sin bufferear el body completo en RAM                 │
│   - Flush por chunk: el cliente recibe bytes ya           │
└─────────┬─────────────────────────────────────────────────┘
          │  HTTP response body
          │  Content-Disposition: attachment; filename="..."
          ▼
┌────────────────────┐
│  Navegador del     │
│  usuario final     │   dispara la descarga / preview
└────────────────────┘
```

Puntos clave:

- **El backend nunca persiste el archivo.** Lee del origen y escribe al response al mismo tiempo.
- **El cliente no conoce el origen.** Solo ve tu URL pública. Los tokens, llaves y SAS quedan en el servidor.
- **Streaming real.** Si el archivo pesa 500 MB, tu RAM no se mueve más allá del chunk size (8 KiB por default). El navegador empieza a recibir bytes inmediatamente, no después de descargarlo todo a tu servidor.
- **Misma idea que `response()->streamDownload(...)` de Laravel**, pero universal entre frameworks Java y con providers para las nubes principales ya empaquetados.

---

## Equivalente con Laravel

Side by side. El controller Java de la derecha es **funcionalmente idéntico** al de Laravel.

### 1. Stream de un archivo remoto autenticado

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

### 2. Con autenticación Bearer

```php
// Laravel + Guzzle
$client = new Client(['headers' => ['Authorization' => 'Bearer '.$token]]);
return $stream($url, $name);   // tu StreamRemoteDownload espera la URL ya autenticada
```

```java
// Spring / Quarkus — el source lleva la auth
DownloadOrigin src = HttpOrigin.url(url).bearer(token).build();
return Downloads.attachment(src, name);
```

### 3. Desde Amazon S3

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

### 4. Vista previa en el navegador (inline)

```php
// Laravel
return response()->file($path);
```

```java
// Spring / Quarkus
return Downloads.inline(src, "preview.pdf");
```

---

## Módulos

| Módulo | Descripción | Dependencias añadidas |
| --- | --- | --- |
| **`remote-download-core`** | API universal + HTTP via JDK HttpClient (Basic / Bearer / headers) + progress + checksum | Solo `slf4j-api` |
| `remote-download-apache` | HTTP enterprise: retries con backoff, NTLM, Kerberos, proxy con auth, timeouts granulares | Apache HttpClient 5 |
| `remote-download-s3` | Amazon S3 con default credentials chain (env, profile, IAM) | AWS SDK v2 (`s3`) |
| `remote-download-azure` | Azure Blob (Connection String, SAS) | `azure-storage-blob` |
| `remote-download-sftp` | SFTP con autenticación por password o llave privada | Apache Mina SSHD |
| `remote-download-ftp` | FTP / FTPS con modo pasivo, transferencias binarias | Apache Commons Net |
| `remote-download-gcs` | Google Cloud Storage (ADC, service account JSON, workload identity) | `google-cloud-storage` |
| `remote-download-spring` | Spring Boot starter: auto-config + `ConfigurationProperties` + facade estático | `spring-boot-autoconfigure` + `spring-web` |
| `remote-download-quarkus` | Quarkus / JAX-RS / CDI: bean `@ApplicationScoped` + helpers JAX-RS + facade | Jakarta CDI + JAX-RS + MicroProfile Config |

`core` es obligatorio. Todos los demás son opcionales y aditivos.

---

## Quick start

### 1. Agrega el módulo que necesites

Para una app Spring Boot que descarga archivos públicos:

```xml
<dependency>
  <groupId>com.github.calcifux.remote-download-java</groupId>
  <artifactId>remote-download-core</artifactId>
  <version>v1.0.1</version>
</dependency>
<dependency>
  <groupId>com.github.calcifux.remote-download-java</groupId>
  <artifactId>remote-download-spring</artifactId>
  <version>v1.0.1</version>
</dependency>
```

### 2. Úsalo en un controller

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

Eso es todo. La auto-configuración de Spring se encarga del resto.

---

## Conceptos

### `DownloadOrigin`

Interfaz central. Cualquier cosa que pueda producir bytes remotos la implementa: una URL HTTP, un objeto S3, un blob de Azure, un objeto de GCS, o lo que tú escribas.

```java
public interface DownloadOrigin {
    RemoteContent open() throws IOException;
}
```

### `RemoteContent`

Wrapper `AutoCloseable` con el `InputStream` vivo + metadatos (content-type, length, filename). Se cierra con try-with-resources y libera la conexión subyacente.

### `RemoteDownload`

Entry point estático con dos factory methods:

```java
RemoteDownload.from(String url)        // atajo: HttpOrigin público
RemoteDownload.from(DownloadOrigin src)  // cualquier source
```

Devuelve un `RemoteDownloadRequest` con tres formas de consumir el contenido.

---

## API universal

```java
// 1. Forma común: copia todo al OutputStream destino y libera recursos
WriteResult result = RemoteDownload.from(src).writeTo(outputStream);
log.info("transferred {} bytes in {}", result.getBytesTransferred(), result.getDuration());

// 2. Tamaño de chunk personalizado
RemoteDownload.from(src).chunkSize(16 * 1024).writeTo(outputStream);

// 3. Si necesitas metadata + stream
try (RemoteContent c = RemoteDownload.from(src).fetch()) {
    System.out.println("size:        " + c.contentLength().orElse(-1L));
    System.out.println("contentType: " + c.contentType().orElse("?"));
    System.out.println("filename:    " + c.filename().orElse("?"));
    c.getInputStream().transferTo(outputStream);
}

// 4. Solo el InputStream para alimentar otra librería
try (InputStream in = RemoteDownload.from(src).asInputStream()) {
    PDDocument doc = PDDocument.load(in);
}
```

### Progreso

```java
RemoteDownload.from(src)
    .onProgress((bytesRead, totalBytes) -> {
        long pct = totalBytes != null ? (bytesRead * 100L / totalBytes) : -1;
        log.info("downloaded {} / {} bytes ({}%)", bytesRead, totalBytes, pct);
    })
    .writeTo(outputStream);
```

`totalBytes` puede ser `null` cuando el origen no advertise `Content-Length` (algunos endpoints HTTP con `Transfer-Encoding: chunked`, ciertos servidores SFTP).

### Checksum

```java
WriteResult result = RemoteDownload.from(src)
    .checksum("SHA-256")
    .writeTo(outputStream);

result.checksum().ifPresent(hex ->
    log.info("file integrity hash: {}", hex)
);
```

El digest se calcula **mientras los bytes pasan** por el pipe, sin pasada extra. Algoritmos válidos son los que `java.security.MessageDigest` reconozca: `MD5`, `SHA-1`, `SHA-256`, `SHA-512`, etc.

### Combinar progreso + checksum + chunk custom

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

## Fuentes (DownloadOrigin)

### HTTP simple (`remote-download-core`)

```java
// URL pública
HttpOrigin.url("https://cdn.example.com/file.pdf").build();

// Con bearer token
HttpOrigin.url(url).bearer("eyJhbGc...").build();

// Con basic auth
HttpOrigin.url(url).basicAuth("user", "pass").build();

// Headers custom + timeouts
HttpOrigin.url(url)
    .header("X-Tenant", "acme")
    .header("Accept-Language", "es-MX")
    .connectTimeout(Duration.ofSeconds(10))
    .requestTimeout(Duration.ofMinutes(2))
    .build();
```

### HTTP enterprise (`remote-download-apache`)

```java
ApacheHttpOrigin.url(url)
    .ntlm("CORP", "user", "pass")        // SharePoint, intranet Windows
    .proxy("proxy.corp", 8080)
    .proxyAuth("proxyUser", "proxyPass")
    .retries(3)                          // backoff con DefaultHttpRequestRetryStrategy
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

// Credenciales explícitas
S3Origin.builder()
    .bucket("...").key("...").region("us-west-2")
    .credentials(accessKey, secretKey)
    .build();

// MinIO / LocalStack / S3-compatibles
S3Origin.builder()
    .bucket("...").key("...").region("us-east-1")
    .endpoint("http://localhost:9000")
    .credentials("minioadmin", "minioadmin")
    .build();
```

### Azure Blob (`remote-download-azure`)

```java
// Connection String (modo más común)
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

// Service account JSON desde classpath
GcsOrigin.builder()
    .bucket("...").object("...")
    .credentialsPath("classpath:gcp/sa-key.json")
    .build();

// Service account JSON desde el filesystem
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

// Llave privada (PEM en el filesystem)
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
// FTP plano
FtpOrigin.builder()
    .host("ftp.example.com")
    .user("anonymous")
    .password("guest@example.com")
    .path("/pub/file.zip")
    .build();

// FTPS (TLS sobre el control channel)
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

## Patrón `response()->download()` en Java

Esta es la sección que probablemente más te interesa: cómo replicar de forma idiomática lo que hace Laravel con `response()->download()`, `response()->streamDownload()` y `response()->file()`.

### Equivalencias rápidas

| Laravel | Spring (static factory) | Quarkus (static factory) |
| --- | --- | --- |
| `response()->download($path, $name)` | `Downloads.attachment(src, name)` | `Downloads.attachment(src, name)` |
| `response()->streamDownload($cb, $name)` | `Downloads.attachment(src, name)` | `Downloads.attachment(src, name)` |
| `response()->file($path)` | `Downloads.inline(src, name)` | `Downloads.inline(src, name)` |
| `response()->stream($cb)` | `Downloads.stream(src)` | `Downloads.stream(src)` |

> `Downloads` es una **utility class con métodos estáticos** (igual que `ResponseEntity.ok()` o `List.of()`). Sin estado global, sin inicialización, cada llamada construye una nueva response. Si necesitas configurar el chunk size desde `application.yml`, inyecta el bean `RemoteDownloadService` (Spring) o `RemoteDownloadJaxRsService` (Quarkus) — ambos coexisten con la utility class.

### Tres modos de respuesta

#### `attachment(source, filename)` — fuerza descarga

```java
return Downloads.attachment(src, "factura-2026-001.pdf");
```

Genera el header:

```
Content-Disposition: attachment; filename="factura-2026-001.pdf"; filename*=UTF-8''factura-2026-001.pdf
```

El navegador siempre descarga el archivo, nunca lo intenta abrir inline.

#### `inline(source, filename)` — vista previa en navegador

```java
return Downloads.inline(src, "preview.pdf");
```

```
Content-Disposition: inline; filename="preview.pdf"; filename*=UTF-8''preview.pdf
```

El navegador renderiza el PDF / imagen / video si tiene plugin nativo; de lo contrario lo descarga.

#### `stream(source)` — sin disposition

```java
return Downloads.stream(src);
```

No agrega `Content-Disposition`. Útil para forwarding interno entre microservicios o cuando el cliente decidirá qué hacer.

### Filenames con acentos / espacios / caracteres especiales

`remote-download` siempre emite **dos formas** del filename — la legacy y la `RFC 5987` `filename*=UTF-8''` — para que tanto navegadores antiguos como modernos lo manejen bien:

```java
return Downloads.attachment(src, "Reporte trimestral – 2026.pdf");
```

```
Content-Disposition: attachment;
    filename="Reporte trimestral – 2026.pdf";
    filename*=UTF-8''Reporte%20trimestral%20%E2%80%93%202026.pdf
```

### Static factory vs bean inyectado

Las dos formas son válidas; usa la que te quede más cómoda:

```java
// 1) Static factory (estilo Laravel, una linea, defaults hardcoded)
@GetMapping("/download/{id}")
public ResponseEntity<StreamingResponseBody> download(@PathVariable String id) {
    return Downloads.attachment(buildSource(id), id + ".pdf");
}

// 2) Bean inyectado (idiomatico Spring, lee chunk-size de application.yml)
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

> Para overridear el chunk size con la static factory pasa el tercer
> argumento: `Downloads.attachment(src, name, 16 * 1024)`.

### Headers extra

Si necesitas headers más allá de `Content-Disposition`, devuelve la response del facade y agrégalos:

```java
ResponseEntity<StreamingResponseBody> resp = Downloads.attachment(src, name);
return ResponseEntity.ok()
    .headers(resp.getHeaders())
    .header("X-Doc-Version", version)
    .header("Cache-Control", "private, max-age=0, no-cache")
    .contentType(MediaType.APPLICATION_PDF)
    .body(resp.getBody());
```

### Ejemplo end-to-end: PDF firmado desde S3 con auditoría

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

## Uso por framework

### Spring Boot (con starter)

```xml
<dependency>
  <groupId>com.github.calcifux.remote-download-java</groupId>
  <artifactId>remote-download-spring</artifactId>
  <version>v1.0.1</version>
</dependency>
```

```java
@RestController
public class FilesController {

    // 1. Static factory (utility class, sin inyectar nada)
    @GetMapping("/download/{id}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String id) {
        return Downloads.attachment(buildSource(id), id + ".pdf");
    }

    // 2. Bean inyectado (chunk size + disposition desde application.yml)
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
  <version>v1.0.1</version>
</dependency>
```

```java
@Path("/files")
public class FilesResource {

    // 1. Static factory (utility class, sin inyectar nada)
    @GET @Path("/{id}")
    public Response download(@PathParam("id") String id) {
        return Downloads.attachment(buildSource(id), id + ".pdf");
    }

    // 2. Bean inyectado (chunk size + disposition desde application.properties)
    @Inject RemoteDownloadJaxRsService streamer;

    @GET @Path("/preview/{id}")
    public Response preview(@PathParam("id") String id) {
        return streamer.inline(buildSource(id), "preview.pdf");
    }
}
```

### JAX-RS plano (Helidon, OpenLiberty, etc.)

El módulo `remote-download-quarkus` solo depende de Jakarta CDI + JAX-RS + MicroProfile Config, así que funciona idéntico fuera de Quarkus.

### Servlet plano

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

### Casos secundarios (no es el uso central)

> Aunque la librería existe para hacer **proxy de bytes hacia el navegador**, el `DownloadOrigin` produce un `InputStream` real, así que también se puede consumir de otras maneras cuando lo necesites.

**Bajar un archivo a disco desde un job batch / CLI / Lambda:**

```java
public static void main(String[] args) throws IOException {
    DownloadOrigin src = HttpOrigin.url(args[0]).build();
    try (FileOutputStream fos = new FileOutputStream(args[1])) {
        long bytes = RemoteDownload.from(src).writeTo(fos);
        System.out.printf("%d bytes -> %s%n", bytes, args[1]);
    }
}
```

**Alimentar a otra librería con el `InputStream`** (sin guardar a disco):

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

> En estos casos pierdes la propiedad de "el cliente no ve el origen" — porque el cliente aquí es tu propio proceso. Para el escenario de descarga desde el navegador, usa siempre `writeTo(response.getOutputStream())` o el facade.

---

## Configuración por properties

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

### Variables disponibles

| Property | Default | Descripción |
| --- | --- | --- |
| `remote-download.enabled` | `true` | Habilita o deshabilita la auto-config (solo Spring) |
| `remote-download.chunk-size` | `8192` | Bytes por iteración al copiar contenido |
| `remote-download.default-disposition` | `attachment` | `attachment` o `inline` cuando no se especifica |

---

## Custom DownloadOrigin

¿Necesitas FTP, SFTP, Dropbox, SharePoint, una API REST exótica? Implementa la interfaz:

```java
public class FtpSource implements DownloadOrigin {

    private final String host;
    private final String path;
    private final String user;
    private final String pass;

    @Override
    public RemoteContent open() throws IOException {
        FTPClient ftp = new FTPClient();
        ftp.connect(host);
        ftp.login(user, pass);
        ftp.enterLocalPassiveMode();

        InputStream stream = ftp.retrieveFileStream(path);
        if (stream == null) {
            ftp.disconnect();
            throw new IOException("FTP: no se pudo abrir " + path);
        }

        return RemoteContent.builder()
            .inputStream(stream)
            .filename(extractFilename(path))
            .onClose(() -> {
                try { ftp.completePendingCommand(); } catch (IOException ignored) {}
                try { ftp.disconnect(); } catch (IOException ignored) {}
            })
            .build();
    }
}
```

Luego se usa exactamente igual:

```java
DownloadOrigin src = new FtpSource(host, path, user, pass);
return Downloads.attachment(src, "report.csv");
```

---

## Estructura del repo

```
remote-download-utils-java21/
├── pom.xml                      (parent, packaging=pom)
├── README.md
├── .gitignore
│
├── remote-download-core/
│   └── src/main/java/com/github/calcifux/remotedownload/
│       ├── RemoteDownload.java
│       ├── RemoteDownloadRequest.java
│       ├── RemoteDownloadException.java
│       ├── DownloadOrigin.java
│       ├── RemoteContent.java
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
├── remote-download-spring/
│   ├── ...spring/Downloads.java                    (static factory)
│   ├── ...spring/config/RemoteDownloadAutoConfiguration.java
│   ├── ...spring/config/RemoteDownloadProperties.java
│   ├── ...spring/core/RemoteDownloadService.java             (injectable bean)
│   └── resources/META-INF/spring/...AutoConfiguration.imports
│
└── remote-download-quarkus/
    ├── ...quarkus/Downloads.java                    (static factory)
    └── ...quarkus/core/RemoteDownloadJaxRsService.java        (CDI bean)
```

---

## Licencia

MIT — usa, copia, modifica.
