package com.meerkatgramv2post.domain.post.controller;

import com.meerkatgramv2post.domain.post.request.PostIndexRequestDTO;
import com.meerkatgramv2post.domain.post.response.PostIndexResponseDTO;
import com.meerkatgramv2post.domain.post.service.PostService;
import com.meerkatgramv2post.global.response.GlobalResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
