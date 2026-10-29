package io.atomity.blogcms.media.controller;

import io.atomity.blogcms.media.dto.MediaResponse;
import io.atomity.blogcms.media.dto.UpdateMediaRequest;
import io.atomity.blogcms.media.entity.Media;
import io.atomity.blogcms.media.service.MediaService;
import io.atomity.blogcms.media.storage.StorageService;
import io.atomity.blogcms.shared.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;
    private final StorageService storage;

    public MediaController(MediaService mediaService, StorageService storage) {
        this.mediaService = mediaService;
        this.storage = storage;
    }

    @GetMapping
    public ApiResponse<List<MediaResponse>> list() {
        return ApiResponse.ok(mediaService.list());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaResponse>> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(mediaService.upload(file)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<MediaResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateMediaRequest request) {
        return ApiResponse.ok(mediaService.updateAltText(id, request.altText()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        mediaService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * The image itself. Public (see SecurityConfig) so published pages can embed it.
     * A media id never points at different bytes, so it can be cached forever.
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable UUID id) {
        Media media = mediaService.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.getContentType()))
                .contentLength(media.getSizeBytes())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionHeader(media.getOriginalFilename()))
                .header("X-Content-Type-Options", "nosniff")
                .body(storage.load(media.getStorageKey()));
    }

    public static String contentDispositionHeader(String originalFilename) {
        String asciiFallback = toAsciiFallback(originalFilename);
        String base = ContentDisposition.inline().filename(asciiFallback).build().toString();
        if (isPureAsciiWithoutSpecial(originalFilename) && originalFilename.equals(asciiFallback)) {
            return base;
        }
        String encoded = UriUtils.encode(originalFilename, StandardCharsets.UTF_8);
        return base + "; filename*=UTF-8''" + encoded;
    }

    private static boolean isPureAsciiWithoutSpecial(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c > 0x7E || c == '"' || c == '\\' || c == '%' || c == ';') {
                return false;
            }
        }
        return true;
    }

    private static String toAsciiFallback(String filename) {
        if (filename == null || filename.isBlank()) {
            return "image";
        }
        String ascii = Normalizer.normalize(filename, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]", "_");
        String baseName = ascii.contains(".") ? ascii.substring(0, ascii.lastIndexOf('.')) : ascii;
        if (baseName.replace("_", "").isBlank()) {
            String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : "";
            String asciiExt = ext.replaceAll("[^\\x20-\\x7E]", "");
            return "image" + (asciiExt.isEmpty() ? "" : asciiExt);
        }
        return ascii;
    }
}
