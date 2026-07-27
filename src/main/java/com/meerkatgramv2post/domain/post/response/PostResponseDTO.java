package com.meerkatgramv2post.domain.post.response;

import com.meerkatgramv2post.domain.post.entity.Post;

import java.time.LocalDateTime;

public record PostResponseDTO(
    Long id,
    String content,
    String image,
    Long userId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
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
