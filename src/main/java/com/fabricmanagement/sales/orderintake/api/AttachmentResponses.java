package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.sales.orderintake.app.IntakeAttachmentService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** A stored file as an attachment download that is never rendered inline. */
final class AttachmentResponses {

  private AttachmentResponses() {}

  static ResponseEntity<byte[]> of(IntakeAttachmentService.Download download) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(download.contentType()));
    headers.setContentDisposition(
        ContentDisposition.attachment()
            .filename(download.fileName(), StandardCharsets.UTF_8)
            .build());
    headers.set("X-Content-Type-Options", "nosniff");
    return ResponseEntity.ok().headers(headers).body(download.content());
  }
}
