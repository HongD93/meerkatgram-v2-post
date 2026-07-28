package com.meerkatgramv2post.global.minio;

import com.meerkatgramv2post.global.error.custom.FileManagedException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MinioManager {
    private final MinioConfig minioConfig;
    private final MinioClient minioClient;

    /**
     * 파일 확장자 추출 및 파일 검증
     * @param file
     * @return 확장자(소문자)
     */
    public String extractExtension(MultipartFile file) {
        // 파일 존재 체크
        if(file == null || file.isEmpty()) {
            throw new FileManagedException("파일 업로드 실패: 파일 없음");
        }

        // 파일 확장자 검증
        String fileName = file.getOriginalFilename();
        if(fileName == null || !fileName.contains(".")) {
            throw new FileManagedException("파일 업로드 실패: 파일명 이상");
        }
        String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

        // 허용 확장자 검증
        if(!minioConfig.allowImageExtensions().contains("image/" + fileExtension)) {
            throw new FileManagedException("파일 업로드 실패: 허용하지 않는 확장자");
        }

        return fileExtension;
    }

    /**
     * 랜덤 파일명 생성(확장자 없음)
     * @return 'yyyyMMdd_파일명' 형식
     */
    public String generateFileName() {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate now = LocalDate.now();

        return now.format(dateFormatter) + "_" + UUID.randomUUID();
    }

    public String generateObjectKey(MultipartFile file) {
        Path path = Path.of(minioConfig.minioImagePath(), this.generateFileName() + "." + this.extractExtension(file));
        return path.toString().replace(File.separator, "/");
    }

    public void uploadFile(String objectKey, MultipartFile file) {
        try(InputStream inputStream = file.getInputStream()) {
            minioClient
                .putObject(
                    PutObjectArgs.builder()
                        .bucket(minioConfig.minioBucket())   // 파일이 저장될 minIO의 버킷명
                        .object(objectKey)                   // 버킷 내부에서 관리할 전체 저장 경로
                        .stream(
                            inputStream,                     // 업로드할 파일의 InputStream
                            file.getSize(),                  // 업로드할 파일의 크기
                            -1                               // 업로드시 패킷 크기(-1을 Minio SDK가 적절하게 조절해서 전송)
                        )
                        .contentType(file.getContentType())
                        .build()
                );
        } catch (Exception e) {
            throw new FileManagedException("파일 업로드 실패: MinIO 업로드 실패, " + objectKey + " " + e.getMessage());
        }
    }

    public String createMinioObjectUri(String objectKey) {
        Path path = Path.of(minioConfig.minioBucket(), objectKey);

        return String.format(
            "%s/%s",
            minioConfig.minioEndpoint(),
            path.toString().replace(File.separator, "/")
        );
    }

    public void validateImageExistsInMinio(String uri) {
        if(!uri.startsWith(minioConfig.minioEndpoint() + "/" + minioConfig.minioBucket() + minioConfig.minioImagePath())) {
            throw new FileManagedException("파일 확인 처리: 파일 경로 이상");
        }

        // URL에서 pure object name (파일 경로)만 추출
        String prefix = minioConfig.minioEndpoint() + "/" + minioConfig.minioBucket() + "/";
        String objectName = uri.substring(prefix.length());

        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(minioConfig.minioBucket())
                    .object(objectName)
                    .build()
            );
        } catch (Exception e) {
            throw new FileManagedException("파일 확인 처리: MinIO에 실제 존재하지 않는 파일입니다.");
        }
    }
}
