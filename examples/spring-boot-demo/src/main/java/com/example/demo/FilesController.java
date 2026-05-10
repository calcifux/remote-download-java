package com.example.demo;

import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.http.HttpOrigin;
import com.github.calcifux.remotedownload.spring.Downloads;
import com.github.calcifux.remotedownload.spring.core.RemoteDownloadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Demonstration controller. Each endpoint streams a publicly hosted PDF
 * straight to the browser through this backend.
 *
 * <p>The {@code download} endpoint uses the static factory {@link Downloads};
 * {@code preview} uses the auto-configured {@link RemoteDownloadService}
 * bean. Both are equivalent in functionality.
 */
@RestController
public class FilesController {

    /** Public sample PDF used for the demo. */
    private static final String SAMPLE_URL =
            "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf";

    private final RemoteDownloadService streamer;

    public FilesController(RemoteDownloadService streamer) {
        this.streamer = streamer;
    }

    /**
     * Forces a browser download via {@code Content-Disposition: attachment}.
     */
    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> download() {
        DownloadOrigin source = HttpOrigin.url(SAMPLE_URL).build();
        return Downloads.attachment(source, "dummy.pdf");
    }

    /**
     * Inline preview: the browser renders the PDF when it can.
     */
    @GetMapping("/preview")
    public ResponseEntity<StreamingResponseBody> preview() {
        DownloadOrigin source = HttpOrigin.url(SAMPLE_URL).build();
        return streamer.inline(source, "dummy.pdf");
    }

    /**
     * Plain health endpoint.
     */
    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
