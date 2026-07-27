package com.meerkatgramv2post.domain.post.service;

import com.meerkatgramv2post.domain.post.entity.Post;
import com.meerkatgramv2post.domain.post.repository.PostQueryDSLRepository;
import com.meerkatgramv2post.domain.post.repository.PostRepository;
import com.meerkatgramv2post.domain.post.request.PostIndexRequestDTO;
import com.meerkatgramv2post.domain.post.response.PostIndexResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostQueryDSLRepository postQueryDSLRepository;
    private final PostRepository postRepository;

    public PostIndexResponseDTO index(PostIndexRequestDTO postIndexRequestDTO) {
        long offset = (postIndexRequestDTO.page() - 1) * postIndexRequestDTO.limit();

        // 특정 페이지 게시글 조회
        List<Post> result = postQueryDSLRepository.pagination(offset, postIndexRequestDTO.limit());

        // 토탈 및 마지막 페이지 여부 조회
        long total = postRepository.count();
        boolean isLastPage = offset * postIndexRequestDTO.limit() >= total;

        return PostIndexResponseDTO.from(result, total, isLastPage);
    }
}
