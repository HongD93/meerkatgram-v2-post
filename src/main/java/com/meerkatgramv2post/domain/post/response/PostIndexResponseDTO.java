package com.meerkatgramv2post.domain.post.response;

import com.meerkatgramv2post.domain.post.entity.Post;

import java.util.List;

public record PostIndexResponseDTO(
    long total,
    boolean isLastPage,
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
