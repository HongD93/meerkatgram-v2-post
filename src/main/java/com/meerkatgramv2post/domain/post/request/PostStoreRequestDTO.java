package com.meerkatgramv2post.domain.post.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "페이지네이션 Request DTO")
public record PostStoreRequestDTO(
    @Schema(description = "게시글 내용", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "필수 항목입니다.")
    @Size(max = 200)
    String content,

    @Schema(description = "게시글 이미지", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "필수 항목입니다.")
    @Size(max = 200)
    String image
) {}
