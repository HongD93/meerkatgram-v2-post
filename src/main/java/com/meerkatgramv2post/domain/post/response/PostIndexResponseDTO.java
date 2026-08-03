package com.meerkatgramv2post.domain.post.response;

import com.meerkatgramv2post.domain.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "게시글 페이지네이션 Response DTO")
public record PostIndexResponseDTO(
    @Schema(description = "총 게시글수", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    long total,
    @Schema(description = "마지막 페이지 여부", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    boolean isLastPage,
    @Schema(description = "게시글 정보", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    List<PostResponseDTO> posts
) {
    public static PostIndexResponseDTO from(List<Post> posts, long total, boolean isLastPage) {
        return new PostIndexResponseDTO(
            total,
            isLastPage,
            posts.stream().map(PostResponseDTO::from).toList()
        );
    }
}
