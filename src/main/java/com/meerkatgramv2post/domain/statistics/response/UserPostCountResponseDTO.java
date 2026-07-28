package com.meerkatgramv2post.domain.statistics.response;

public record UserPostCountResponseDTO(
    long postCount
) {
    public static UserPostCountResponseDTO from(long count) {
        return new UserPostCountResponseDTO(count);
    }
}
