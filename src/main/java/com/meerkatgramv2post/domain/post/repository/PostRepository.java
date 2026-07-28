package com.meerkatgramv2post.domain.post.repository;

import com.meerkatgramv2post.domain.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
}
