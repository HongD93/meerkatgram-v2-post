package com.meerkatgramv2post.domain.post.controller;

import com.meerkatgramv2post.domain.post.request.PostIndexRequestDTO;
import com.meerkatgramv2post.domain.post.request.PostStoreRequestDTO;
import com.meerkatgramv2post.domain.post.response.PostIndexResponseDTO;
import com.meerkatgramv2post.domain.post.response.PostResponseDTO;
import com.meerkatgramv2post.domain.post.service.PostService;
import com.meerkatgramv2post.global.config.openapi.CustomApiResponse;
import com.meerkatgramv2post.global.response.GlobalResponseDTO;
import com.meerkatgramv2post.global.response.constant.CustomResponseCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class PostController {
    private final PostService postService;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    @GetMapping()
    public ResponseEntity<GlobalResponseDTO<PostIndexResponseDTO>> index(
        @Valid PostIndexRequestDTO postIndexRequestDTO
        ) {
        return ResponseEntity.ok(GlobalResponseDTO.success(postService.index(postIndexRequestDTO)));
    }

    @Operation(summary = "게시글 상세 조회")
    @CustomApiResponse(value = {
        CustomResponseCode.RESOURCE_NOT_FOUND_ERROR
        ,CustomResponseCode.INVALID_PARAMETER_ERROR
        ,CustomResponseCode.UNAUTHENTICATED_ERROR
        ,CustomResponseCode.DB_ERROR
        ,CustomResponseCode.SYSTEM_ERROR
    })
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<GlobalResponseDTO<PostResponseDTO>> show(
        @Parameter(description = "게시글 번호", example = "1") @Min(value = 1, message = "1이상 숫자만 허용합니다.") @PathVariable Long id
    ) {
        return ResponseEntity.ok(GlobalResponseDTO.success(postService.show(id)));
    }

    @Operation(summary = "게시글 작성 처리")
    @CustomApiResponse(value = {
        CustomResponseCode.INVALID_PARAMETER_ERROR
        ,CustomResponseCode.UNAUTHENTICATED_ERROR
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

    @Operation(summary = "게시글 삭제 처리")
    @CustomApiResponse(value = {
        CustomResponseCode.RESOURCE_NOT_FOUND_ERROR
        ,CustomResponseCode.INVALID_PARAMETER_ERROR
        ,CustomResponseCode.UNAUTHENTICATED_ERROR
        ,CustomResponseCode.UNAUTHORIZED_ERROR
        ,CustomResponseCode.DB_ERROR
        ,CustomResponseCode.SYSTEM_ERROR
    })
    @PreAuthorize("hasRole('SUPER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<GlobalResponseDTO<Void>> destroy(
        @Parameter(description = "게시글 번호", example = "1") @Min(value = 1, message = "1이상 숫자만 허용합니다.") @PathVariable Long id,
        Authentication authentication
    ) {
        long userId = Long.parseLong(authentication.getName());
        postService.destroy(id, userId);

        return ResponseEntity.ok(GlobalResponseDTO.success());
    }
}
