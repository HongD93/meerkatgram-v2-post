package com.meerkatgramv2post.domain.file.service;

import com.meerkatgramv2post.domain.file.response.FileResponseDTO;
import com.meerkatgramv2post.global.minio.MinioManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileService {
    private final MinioManager minioManager;

    public FileResponseDTO uploadPostImage(MultipartFile file) {
        String objectKey = minioManager.generateObjectKey(file);

        minioManager.uploadFile(objectKey, file);

        return FileResponseDTO.from(minioManager.createMinioObjectUri(objectKey));
    }
}
