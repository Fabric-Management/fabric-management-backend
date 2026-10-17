package com.fabricmanagement.platform.tenant.infra.client;

import com.fabricmanagement.platform.tenant.domain.BrandSvgOutlinePort;
import com.fabricmanagement.platform.tenant.domain.TenantBrandAsset;
import com.fabricmanagement.platform.tenant.domain.exception.TenantBrandingDomainException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Bounded subprocess; only the closed static/text SVG subset reaches the renderer. */
@Component
@Slf4j
public class LocalBrandSvgOutlineClient implements BrandSvgOutlinePort {
  private final Semaphore capacity = new Semaphore(2);
  private final String python;
  private final String inkscape;
  private final String fontMatch;

  public LocalBrandSvgOutlineClient(
      @Value("${branding.outline.python:python3}") String python,
      @Value("${branding.outline.inkscape:inkscape}") String inkscape,
      @Value("${branding.outline.font-match:fc-match}") String fontMatch) {
    this.python = python;
    this.inkscape = inkscape;
    this.fontMatch = fontMatch;
  }

  @Override
  public String outline(String svg) {
    if (!capacity.tryAcquire()) throw unavailable();
    Path directory = null;
    Process process = null;
    try {
      directory = Files.createTempDirectory("brand-outline-");
      Path worker = directory.resolve("outline.py");
      try (var input = new ClassPathResource("branding/outline.py").getInputStream()) {
        Files.copy(input, worker);
      }
      Path source = directory.resolve("input.svg");
      Path output = directory.resolve("output.svg");
      Files.writeString(source, svg, StandardCharsets.UTF_8);
      var builder =
          new ProcessBuilder(
              python, worker.toString(), source.toString(), output.toString(), inkscape, fontMatch);
      builder.directory(directory.toFile());
      builder.redirectErrorStream(true);
      builder.redirectOutput(directory.resolve("worker.log").toFile());
      builder.environment().put("HOME", directory.toString());
      builder.environment().put("XDG_CACHE_HOME", directory.resolve("cache").toString());
      builder.environment().put("INKSCAPE_PROFILE_DIR", directory.resolve("inkscape").toString());
      process = builder.start();
      if (!process.waitFor(15, TimeUnit.SECONDS)) throw unavailable();
      if (process.exitValue() == 20) {
        throw new TenantBrandingDomainException(
            "The exact font face or required glyphs are unavailable; artwork was not changed",
            "TENANT_BRANDING_FONT_UNAVAILABLE",
            422);
      }
      if (process.exitValue() == 22) {
        throw new TenantBrandingDomainException(
            "This SVG text layout cannot be converted without changing its appearance",
            "TENANT_BRANDING_TEXT_UNSUPPORTED",
            422);
      }
      if (process.exitValue() != 0 || !Files.isRegularFile(output)) throw unavailable();
      if (Files.size(output) > TenantBrandAsset.MAX_BYTES) {
        throw new TenantBrandingDomainException(
            "The outlined SVG exceeds 512 KB", "TENANT_BRANDING_TOO_LARGE", 413);
      }
      return Files.readString(output, StandardCharsets.UTF_8);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (IOException e) {
      throw unavailable();
    } finally {
      if (process != null) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) process.destroyForcibly();
      }
      if (directory != null) {
        try (var paths = Files.walk(directory)) {
          for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
            Files.deleteIfExists(path);
        } catch (IOException e) {
          log.warn("Could not remove SVG conversion temporary directory");
        }
      }
      capacity.release();
    }
  }

  private TenantBrandingDomainException unavailable() {
    return new TenantBrandingDomainException(
        "SVG conversion is unavailable; artwork was not changed",
        "TENANT_BRANDING_CONVERSION_UNAVAILABLE",
        503);
  }
}
