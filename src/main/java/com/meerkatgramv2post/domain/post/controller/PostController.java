package com.meerkatgramv2post.domain.post.controller;

import com.meerkatgramv2post.domain.post.request.PostIndexRequestDTO;
import com.meerkatgramv2post.domain.post.response.PostIndexResponseDTO;
import com.meerkatgramv2post.domain.post.response.PostResponseDTO;
import com.meerkatgramv2post.domain.post.service.PostService;
import com.meerkatgramv2post.global.config.openapi.CustomApiResponse;
import com.meerkatgramv2post.global.response.GlobalResponseDTO;
import com.meerkatgramv2post.global.response.constant.CustomResponseCode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class PostController {
    private final PostService postService;

    @GetMapping()
    public ResponseEntity<GlobalResponseDTO<PostIndexResponseDTO>> index(
        @Valid PostIndexRequestDTO postIndexRequestDTO
        ) {
        return ResponseEntity.ok(GlobalResponseDTO.success(postService.index(postIndexRequestDTO)));
    }

    @Operation(summary = "게시글 작성 처리")
    @CustomApiResponse(value = {
        CustomResponseCode.INVALID_PARAMETER_ERROR
        ,CustomResponseCode.UNAUTHENTICATED_ERROR
        , CustomResponseCode.INVALID_TOKEN_ERROR
        ,CustomResponseCode.DB_ERROR
        ,CustomResponseCode.SYSTEM_ERROR
    })
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<GlobalResponseDTO<PostResponseDTO>> store(
        @Valid @RequestBody PostStoreRequestDTO postStoreRequestDTO,
        Authentication authentication
    ) {
        return ResponseEntity.ok(GlobalResponseDTO.success(postService.store(postStoreRequestDTO, authentication)));
    }
}
