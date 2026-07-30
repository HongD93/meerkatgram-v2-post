package com.meerkatgramv2post.domain.file.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record FileResponseDTO(
    @Schema(description = "이미지 경로", example = "http://localhost:8080/bucket/path/yyyyMMdd_t23jit8j23t.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
    String fileUri
) {
    public static FileResponseDTO from(String fileUri) {
        return new FileResponseDTO(fileUri);
    }
}
