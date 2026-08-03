package com.meerkatgramv2post.domain.post.response;

import com.meerkatgramv2post.domain.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "게시글 Response DTO")
public record PostResponseDTO(
    @Schema(description = "게시글 ID", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    Long id,
    @Schema(description = "게시글 내용", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    String content,
    @Schema(description = "게시글 이미지", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    String image,
    @Schema(description = "게시글 작성자ID", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    Long userId,
    @Schema(description = "게시글 작성일", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    LocalDateTime createdAt,
    @Schema(description = "게시글 수정일", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    LocalDateTime updatedAt,
    @Schema(description = "게시글 삭제일", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    LocalDateTime deletedAt
) {
    public static PostResponseDTO from(Post post) {
        return new PostResponseDTO(
            post.getId(),
            post.getContent(),
            post.getImage(),
            post.getUserId(),
            post.getCreatedAt(),
            post.getUpdatedAt(),
            post.getDeletedAt()
        );
    }
}
