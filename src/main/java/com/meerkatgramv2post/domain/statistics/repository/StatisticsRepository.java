package com.meerkatgramv2post.domain.statistics.repository;

import com.meerkatgramv2post.domain.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatisticsRepository extends JpaRepository<Post, Long> {
    long countByUserId(long userId);
}
