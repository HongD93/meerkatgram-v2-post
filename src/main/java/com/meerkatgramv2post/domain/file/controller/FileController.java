package com.meerkatgramv2post.domain.file.controller;

import com.meerkatgramv2post.domain.file.response.FileResponseDTO;
import com.meerkatgramv2post.domain.file.service.FileService;
import com.meerkatgramv2post.global.config.openapi.CustomApiResponse;
import com.meerkatgramv2post.global.response.GlobalResponseDTO;
import com.meerkatgramv2post.global.response.constant.CustomResponseCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "파일 처리", description = "파일 처리 관련")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts/files")
public class FileController {
    private final FileService fileService;

    @Operation(summary = "게시물 이미지 업로드")
    @CustomApiResponse(value = {
        CustomResponseCode.UNAUTHENTICATED_ERROR,
        CustomResponseCode.FILE_MANAGED_ERROR,
        CustomResponseCode.SYSTEM_ERROR
    })
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/images")
    public ResponseEntity<GlobalResponseDTO<FileResponseDTO>> uploadPostImage(
        @ModelAttribute MultipartFile file
    ) {
        return ResponseEntity.ok(GlobalResponseDTO.success(fileService.uploadPostImage(file)));
    }
}
