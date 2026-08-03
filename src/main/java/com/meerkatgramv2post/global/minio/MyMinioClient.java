package com.meerkatgramv2post.global.minio;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyMinioClient {
    @Bean
    public MinioClient minioClient(MinioConfig minioConfig) {
        return MinioClient.builder()
            .endpoint(minioConfig.minioEndpoint())
            .credentials(minioConfig.minioAccessKey(), minioConfig.minioSecretKey())
            .build();
    }
}
