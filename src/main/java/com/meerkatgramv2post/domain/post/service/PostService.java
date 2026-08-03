package com.meerkatgramv2post.domain.post.service;

import com.meerkatgramv2post.domain.post.entity.Post;
import com.meerkatgramv2post.domain.post.response.PostIndexResponseDTO;
import com.meerkatgramv2post.domain.post.response.PostResponseDTO;
import com.meerkatgramv2post.domain.post.repository.PostQueryDSLRepository;
import com.meerkatgramv2post.domain.post.request.PostIndexRequestDTO;
import com.meerkatgramv2post.domain.post.request.PostStoreRequestDTO;
import com.meerkatgramv2post.domain.statistics.repository.StatisticsRepository;
import com.meerkatgramv2post.global.error.custom.business.NotFoundResourceException;
import com.meerkatgramv2post.global.error.custom.business.ResourceAuthorMismatchException;
import com.meerkatgramv2post.global.minio.MinioManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostQueryDSLRepository postQueryDSLRepository;
    private final StatisticsRepository postRepository;
    private final MinioManager minioManager;

    public PostIndexResponseDTO index(PostIndexRequestDTO postIndexRequestDTO) {
        long offset = (postIndexRequestDTO.page() - 1) * postIndexRequestDTO.limit();

        // 특정 페이지 게시글 조회
        List<Post> result = postQueryDSLRepository.pagination(offset, postIndexRequestDTO.limit());

        // 토탈 및 마지막 페이지 여부 조회
        long total = postRepository.count();
        boolean isLastPage = offset + postIndexRequestDTO.limit() >= total;

        return PostIndexResponseDTO.from(result, total, isLastPage);
    }

    @Transactional(rollbackFor = Exception.class)
    public PostResponseDTO store(PostStoreRequestDTO postStoreRequestDTO, Authentication authentication) {
        // 파일URI 체크
        minioManager.validateImageExistsInMinio(postStoreRequestDTO.image());

        // 게시글 생성 처리
        Post post = new Post();
        post.setContent(postStoreRequestDTO.content());
        post.setImage(postStoreRequestDTO.image());
        post.setUserId(Long.parseLong(authentication.getName()));
        postRepository.save(post);

        return PostResponseDTO.from(post);
    }

    public PostResponseDTO show(Long id) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new NotFoundResourceException("이미 삭제된 게시글: " + id));

        return PostResponseDTO.from(post);
    }

    public void destroy(long id, long userId) {
        // 게시글 조회
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new NotFoundResourceException("이미 삭제된 게시글: " + id));

        // 작성자 체크
        if(post.getUserId() != userId) {
            throw new ResourceAuthorMismatchException("게시글 삭제 실패: 작성자 다름");
        }

        postRepository.delete(post);

        minioManager.removeObject(post.getImage());
    }
}
