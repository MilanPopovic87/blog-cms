package io.atomity.blogcms.post.controller;

import io.atomity.blogcms.post.dto.PostListQuery;
import io.atomity.blogcms.post.dto.PostRequest;
import io.atomity.blogcms.post.dto.PostResponse;
import io.atomity.blogcms.post.dto.PostSummaryResponse;
import io.atomity.blogcms.post.service.PostService;
import io.atomity.blogcms.shared.ApiResponse;
import io.atomity.blogcms.shared.PageMeta;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /** GET /api/v1/posts?status&q&tag&author&featured&sort&direction&page&size — pagination in {@code meta}. */
    @GetMapping
    public ApiResponse<List<PostSummaryResponse>> list(@Valid PostListQuery query) {
        Page<PostSummaryResponse> page = postService.list(query);
        return ApiResponse.ok(page.getContent(), PageMeta.of(page));
    }

    @GetMapping("/counts")
    public ApiResponse<Map<String, Long>> counts() {
        return ApiResponse.ok(postService.countByStatus());
    }

    @GetMapping("/{id}")
    public ApiResponse<PostResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(postService.get(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> create(@Valid @RequestBody PostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(postService.create(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<PostResponse> update(@PathVariable UUID id, @Valid @RequestBody PostRequest request) {
        return ApiResponse.ok(postService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<PostResponse> publish(@PathVariable UUID id) {
        return ApiResponse.ok(postService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    public ApiResponse<PostResponse> unpublish(@PathVariable UUID id) {
        return ApiResponse.ok(postService.unpublish(id));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<PostResponse> archive(@PathVariable UUID id) {
        return ApiResponse.ok(postService.archive(id));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ApiResponse<PostResponse>> duplicate(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(postService.duplicate(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
