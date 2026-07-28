package com.meerkatgramv2post.domain.post.service;

import com.meerkatgramv2post.domain.post.entity.Post;
import com.meerkatgramv2post.domain.post.repository.PostQueryDSLRepository;
import com.meerkatgramv2post.domain.post.repository.PostRepository;
import com.meerkatgramv2post.domain.post.request.PostIndexRequestDTO;
import com.meerkatgramv2post.domain.post.response.PostIndexResponseDTO;
import com.meerkatgramv2post.domain.post.response.PostResponseDTO;
import com.meerkatgramv2post.global.error.custom.FileManagedException;
import com.meerkatgramv2post.global.minio.MinioConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostQueryDSLRepository postQueryDSLRepository;
    private final PostRepository postRepository;
    private final MinioConfig minioConfig;

    public PostIndexResponseDTO index(PostIndexRequestDTO postIndexRequestDTO) {
        long offset = (postIndexRequestDTO.page() - 1) * postIndexRequestDTO.limit();

        // 특정 페이지 게시글 조회
        List<Post> result = postQueryDSLRepository.pagination(offset, postIndexRequestDTO.limit());

        // 토탈 및 마지막 페이지 여부 조회
        long total = postRepository.count();
        boolean isLastPage = offset * postIndexRequestDTO.limit() >= total;

        return PostIndexResponseDTO.from(result, total, isLastPage);

        @Transactional(rollbackFor = Exception.class)
        public PostResponseDTO store(PostStoreRequestDTO postStoreRequestDTO, Authentication authentication) {
            minioManager.validateImageExistsInMinio(postStoreRequestDTO.image());

            long userId = Long.parseLong(authentication.getName());

            Post post = new Post();
            post.setContent(postStoreRequestDTO.content());
            post.setImage(postStoreRequestDTO.image());
            post.setUserId(userId);
            postRepository.save(post);

            return PostResponseDTO.from(post);
        }
    }
}
