# 3. 파일 스토리지 전환 — 로컬 디스크 → MinIO

[MSA 전환](./01-msa-architecture.md)에서 auth-service/post-service로 분리할 때, 파일 도메인(프로필 이미지·게시글 이미지)은 로컬 디스크에 각자 저장하는 방식을 그대로 유지했다. 이번 챕터에서는 그 저장 방식만 **MinIO 오브젝트 스토리지**로 바꾼다 — "파일 도메인을 누가 소유하는가"는 그대로 두고, "어디에 저장하는가"만 바꾼다.

> **전제:** MinIO 서버 자체(설치, 버킷 생성, 접근 정책 설정)는 이미 구축·완료된 상태다. 이 챕터는 인프라 구축이 아니라 **auth-service/post-service가 그 MinIO 서버를 사용하도록 코드를 바꾸는 것**에 집중한다.

**최종 목표 아키텍처:**

```
[Vue 3 프론트엔드]
        │  :5173
        ▼
[api-gateway]  :8080                 ← 신규 라우트 없음 (기존 /api/auth/**, /api/posts/**가 그대로 커버)
   │                 │
   │ /api/auth/...   │ /api/posts/...
   ▼                 ▼
[auth-service] :8081          [post-service] :8082
   │      │                       │      │
   ▼      │                       ▼      │
[auth_db] └───────────┬───────────[post_db]
                       ▼
        [MinIO 공용 버킷 `meerkat`]
        (prefix: /auth/profiles, /post/images)
```

> 버킷은 프로젝트 전체가 공유하는 **`meerkat` 버킷 1개**뿐이다. auth-service/post-service를 나누는 건 별도 버킷이 아니라 `/auth/profiles`, `/post/images` prefix(폴더)다. 접근 자격증명(access key/secret key)도 두 서비스가 동일한 값을 쓴다 — 즉 MinIO가 두 서비스의 접근 범위를 강제로 갈라주지는 않는다. 이 한계와 그 이유는 2-3절에서 다시 짚는다.

---

## 1강. 왜 MinIO인가 — 그리고 왜 별도 서비스로 묶지 않는가

### 학습 목표
- 로컬 디스크 저장 방식이 MSA/k8s 환경에서 왜 깨지는지 설명할 수 있다
- 파일 저장소를 MinIO로 바꾸면서도 "file-service" 같은 별도 서비스를 만들지 않는 이유를 설명할 수 있다

### 1-1. 로컬 디스크의 한계

k8s에서 `replicas: 2`로 auth-service를 띄우면, 각 파드는 서로 다른 컨테이너 파일시스템을 갖는다. 회원가입 시 프로필 이미지가 파드 A의 디스크에 저장됐는데, 이후 조회 요청이 로드밸런싱으로 파드 B에 가면 그 파일을 찾지 못한다. MinIO는 모든 파드가 공유하는 외부 저장소이므로 이 문제가 사라진다.

### 1-2. "그럼 file-service로 묶으면 되지 않나?"에 대한 답

오브젝트 스토리지로 옮기는 김에 프로필/게시글 업로드를 하나의 `file-service`로 통합하는 방법도 검토했지만, **이 프로젝트는 그 방향을 택하지 않는다.** 이유는 [MSA 챕터 1-3](./01-msa-architecture.md)의 서비스 분리 기준을 그대로 적용해보면 드러난다.

| 기준 | file-service로 묶었을 때 |
|------|------------------------|
| **변경 이유가 다른가?** | 프로필 이미지 검증 규칙(예: 정사각형 크롭 강제)과 게시글 이미지 검증 규칙(예: 다중 이미지, 썸네일 생성)은 앞으로 서로 다른 이유로, 서로 다른 시점에 바뀔 가능성이 크다. 두 요구가 하나의 서비스에 섞이면, 게시글 쪽 요구사항 때문에 파일 서비스를 배포할 때 프로필 업로드 경로도 같이 재배포 위험에 노출된다. |
| **독립적으로 배포 가능한가?** | file-service를 담당하는 팀(또는 인프라 팀)이 auth 팀·post 팀 각각의 파일 요구사항 변화를 매번 알아야 조율이 가능하다 — 정작 그 요구를 가장 잘 아는 건 각 도메인 팀이다. |
| **DB(자원)를 독립적으로 가질 수 있는가?** | 완전히는 아니다 — 버킷과 자격증명은 프로젝트 공용 1개를 공유한다. 대신 `/auth/profiles`, `/post/images` prefix(폴더)로 저장 위치를 나눠 논리적 소유 경계를 유지한다. DB처럼 물리적으로 분리되진 않지만, "이 prefix는 이 서비스만 쓴다"는 관례는 지킨다 — 파일마다 별도 서비스가 있어야 한다는 뜻은 아니다. |

> **핵심:** MSA에서 "공용 인프라를 여러 서비스가 함께 쓴다"와 "공용 인프라를 대신 써주는 별도 서비스를 만든다"는 다른 이야기다. MySQL은 auth-service와 post-service가 함께 쓰는 공용 인프라이지만, 그렇다고 "DB 접근을 대신 해주는 db-service"를 만들지는 않는다. MinIO도 마찬가지로 취급한다 — **공용 인프라(버킷, 자격증명)는 공유하되, 그 인프라를 어떻게 쓸지에 대한 로직(검증 규칙, prefix 관례)은 각 서비스가 직접 소유**한다.

### 1-3. 자격증명 발급은 일회성 요청일 뿐, 지속적인 조율이 아니다

"파일 서버를 구축하는 팀이 각 서비스가 뭘 필요로 하는지 알기 어렵다"는 우려는 타당하지만, 그건 **file-service를 만들 때만 생기는 문제**다. MinIO에 직접 연동하는 이 방식에서 인프라 팀에게 요청할 일은 딱 하나, 최초 1회다:

```
"meerkat 버킷 1개를 만들어주고,
 auth-service/post-service가 함께 쓸 자격증명(read/write)을
 발급해달라 — 서비스 구분은 /auth/profiles, /post/images prefix로 코드에서 알아서 한다"
```

이후 각 서비스가 자기 prefix 안에서 검증 규칙을 어떻게 바꾸든, 파일이 몇 개가 되든, 인프라 팀에게 다시 요청할 일이 없다 — 그 판단은 전적으로 각 도메인 서비스 코드 안에서 이뤄진다.

### 1강 정리
- 로컬 디스크는 k8s 다중 파드 환경에서 구조적으로 깨진다 — MinIO 도입으로 해결
- MinIO는 MySQL처럼 **공용 인프라**로 취급한다 — 공용 인프라를 대신 써주는 별도 서비스(file-service)는 만들지 않는다
- 파일 도메인 소유권은 MSA 챕터에서 정한 대로 유지(프로필=auth-service, 게시글=post-service), 저장 방식만 교체
- 인프라 팀에게 필요한 건 공용 버킷(`meerkat`) 생성 + 공용 자격증명 발급, 최초 1회 요청뿐

---

## 2강. `MinioManager`/`MinioFileManager` 구현과 각 서비스 적용

### 학습 목표
- 로컬 디스크 서빙 코드를 MinIO 클라이언트 기반 매니저 클래스로 교체할 수 있다
- auth-service/post-service 각각에 동일한 패턴을 적용하고, 업로드 경로를 도메인 하위로 정리할 수 있다

### 2-1. 적용 대상 정리

| 서비스 | 버킷 | prefix | 업로드 경로 |
|--------|------|--------|----------------------|
| auth-service | `meerkat` (공용) | `/auth/profiles` | `POST /api/auth/files/profiles` |
| post-service | `meerkat` (공용) | `/post/images` | `POST /api/posts/files/images` |

> 경로를 각 서비스 네임스페이스(`/api/auth/**`, `/api/posts/**`) 하위로 둔다 — 그래야 Gateway의 기존 라우트가 별도 설정 없이 그대로 커버한다.

### 2-2. `MinioManager`/`MinioFileManager` — 로컬 디스크 서빙 코드 대체

확장자 검증, 파일명 생성 로직은 오브젝트 스토리지에서도 그대로 유효하다. 바뀌는 건 저장 방식뿐이다. 이 클래스는 auth-service, post-service에 같은 패턴으로 각각 들어가지만, **클래스 이름은 서비스마다 다르다** — auth-service는 `MinioManager`, post-service는 `MinioFileManager`다.

```
// build.gradle (의존성 추가)
dependencies {
    // Minio SDK
	implementation "io.minio:minio:8.6.0"
}
```

```java
// global/minio/MinioManager.java (auth-service)
@Component
@RequiredArgsConstructor
public class MinioManager {
    private final MinioConfig minioConfig;
    private final MinioClient minioClient;   // 2-3 참고 — 요청마다 새로 만들지 않고 싱글턴 빈으로 주입받는다

    // ① 확장자 검증 — v1 LocalFileManager와 동일
    public String extractExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileManagedException("파일 업로드 실패: 파일 없음");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.contains(".")) {
            throw new FileManagedException("파일 업로드 실패: 파일명 이상");
        }
        String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        if (!minioConfig.allowImageExtensions().contains("image/" + fileExtension)) {
            throw new FileManagedException("파일 업로드 실패: 허용하지 않는 확장자");
        }
        return fileExtension;
    }

    // ② 논리 경로 생성 — 버킷은 프로젝트 공용 1개라, minioProfilePath(prefix)로 서비스별 폴더를 구분한다
    public String generateObjectKey(MultipartFile file) {
        Path path = Path.of(minioConfig.minioProfilePath(), generateFileName() + "." + extractExtension(file)).normalize();
        return path.toString().replace(File.separator, "/");
    }

    // ③ 디렉토리 생성 로직은 삭제 — 오브젝트 스토리지엔 디렉토리 개념이 없다

    // ④ 파일 실제 저장 — 로컬 디스크 쓰기 → MinIO PutObject 호출로 교체
    public void uploadFile(String objectKey, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(minioConfig.minioBucket())   // 버킷은 공용 1개 — objectKey의 prefix가 서비스를 구분한다
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build()
            );
        } catch (Exception e) {
            throw new FileManagedException("파일 업로드 실패: MinIO 업로드 실패, " + objectKey + " " + e.getMessage());
        }
    }

    // ⑤ 저장된 오브젝트의 접근 URL 조립 — FileService가 아니라 여기서 만든다(2-4 참고)
    public String createMinioObjectUri(String objectKey) {
        Path path = Path.of(minioConfig.minioBucket(), objectKey);
        return String.format("%s/%s", minioConfig.minioEndpoint(), path.toString().replace(File.separator, "/"));
    }
}
```

> post-service의 `MinioFileManager`도 메서드 구성은 동일하다 — 다른 점은 클래스 이름과, `generateObjectKey()`가 참조하는 필드가 `minioConfig.minioProfilePath()`가 아니라 `minioConfig.minioImagePath()`라는 것뿐이다(2-3 참고).

> **`MinioClient`를 매 요청마다 새로 만들지 않는 이유:** `MinioClient`는 내부적으로 커넥션(OkHttp 클라이언트)을 들고 있는 객체라, 업로드 요청마다 새로 생성하면 커넥션을 매번 새로 맺어 재사용 이점이 없다. 그래서 설정값은 `MinioConfig`(2-3)에 순수 데이터로만 두고, `MinioClient` 싱글턴 생성은 별도의 `MyMinioClient`(2-3)로 분리한다 — 책임은 나뉘고, 커넥션은 재사용된다.

### 2-3. `MinioConfig`/`MyMinioClient` — 공용 MinIO 접속 정보

```java
// global/minio/MinioConfig.java (auth-service)
@ConfigurationProperties(prefix = "minio")
public record MinioConfig(
    String minioEndpoint,               // 공용 MinIO 서버 주소 (이미 구축된 서버)
    String minioBucket,                 // 공용 버킷 — auth-service/post-service 동일 값(meerkat)
    String minioAccessKey,              // 공용 자격증명 — auth-service/post-service 동일 값
    String minioSecretKey,              // 공용 자격증명 — auth-service/post-service 동일 값
    String minioProfilePath,            // auth-service만의 필드명 — post-service는 minioImagePath
    List<String> allowImageExtensions
) {}
```

> post-service의 `MinioConfig`(`global/util/file/MinioConfig.java`)는 `minioProfilePath` 자리에 `minioImagePath`라는 이름을 쓴다는 것만 다르고 나머지 필드는 동일하다.

```yaml
# auth-service/src/main/resources/application.yaml (추가분)
minio:
  minio-endpoint: ${MINIO_ENDPOINT}
  minio-bucket: ${MINIO_BUCKET}
  minio-access-key: ${MINIO_ACCESS_KEY}     # auth-service/post-service 공용 자격증명
  minio-secret-key: ${MINIO_SECRET_KEY}
  minio-profile-path: ${MINIO_PROFILE_PATH}
  allow-image-extensions: [
    "image/jpg", "image/jpeg", "image/png", "image/gif", "image/webp"
  ]
```

```yaml
# post-service/src/main/resources/application.yaml (추가분)
minio:
  minio-endpoint: ${MINIO_ENDPOINT}
  minio-bucket: ${MINIO_BUCKET}
  minio-access-key: ${MINIO_ACCESS_KEY}
  minio-secret-key: ${MINIO_SECRET_KEY}
  minio-image-path: ${MINIO_IMAGE_PATH}
  allow-image-extensions: [
    "image/jpg", "image/jpeg", "image/png", "image/gif", "image/webp"
  ]
```

```java
// global/minio/MyMinioClient.java (post-service는 global/util/file/MyMinioClient.java — 코드는 완전히 동일)
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
```

> **버킷·자격증명은 공용, 격리는 prefix 관례로만.** auth-service와 post-service는 같은 버킷·같은 access key/secret key를 쓴다. MinIO 자체는 두 서비스의 접근 범위를 나눠주지 않는다 — `/auth/profiles`, `/post/images` prefix로 저장 위치를 나누는 건 코드 상의 약속이지, 뚫렸을 때 상대 영역을 지켜주는 보안 경계가 아니다. 이 결정은 최소 권한보다 **초기 인프라 요청/운영 비용을 낮추는 쪽을 택한 것**에 가깝다.

### 2-4. `FileService` — public 버킷 + 영구 URL

버킷은 public-read로 열어 영구 URL을 그대로 DB에 저장하는 방식을 택한다 — presigned URL(만료됨)보다 단순하고, 커뮤니티 게시판처럼 "이미지가 어차피 공개로 보여지는" 서비스 특성에도 맞는다.

```java
// domain/file/service/FileService.java (auth-service)
@Service
@RequiredArgsConstructor
public class FileService {
    private final MinioManager minioManager;

    public FileResponseDTO uploadProfile(MultipartFile file) {
        String objectKey = minioManager.generateObjectKey(file);
        minioManager.uploadFile(objectKey, file);
        return FileResponseDTO.from(minioManager.createMinioObjectUri(objectKey));
    }
}
```

```java
// domain/file/services/FileService.java (post-service)
@Service
@RequiredArgsConstructor
public class FileService {
    private final MinioFileManager minioFileManager;

    public FileRes storePosts(MultipartFile file) {
        String objectKey = minioFileManager.generateObjectKey(file);
        minioFileManager.uploadFile(objectKey, file);
        return FileRes.builder()
            .fileUri(minioFileManager.createMinioObjectUri(objectKey))
            .build();
    }
}
```

> URL 조립은 `FileService`가 아니라 매니저 클래스의 `createMinioObjectUri()`가 맡는다. **로컬 디스크 서빙용 정적 리소스 설정은 통째로 삭제한다.** MinIO는 그 자체로 HTTP 서버라 객체 URL(`{endpoint}/{bucket}/{objectKey}`)에 직접 접근하면 MinIO가 파일을 응답한다.

### 2-5. `FileController` — 경로만 도메인 하위로 이동

```java
// domain/file/controller/FileController.java (auth-service)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/files")
public class FileController {
    private final FileService fileService;

    @PostMapping("/profiles")   // 최종 경로: POST /api/auth/files/profiles
    public ResponseEntity<GlobalResponseDTO<FileResponseDTO>> uploadProfile(
        @ModelAttribute MultipartFile file
    ) {
        return ResponseEntity.ok(GlobalResponseDTO.success(fileService.uploadProfile(file)));
    }
}
```

```java
// domain/file/controllers/FileController.java (post-service)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts/files")
public class FileController {
    private final FileService fileService;

    @PostMapping("/images")   // 최종 경로: POST /api/posts/files/images
    public ResponseEntity<GlobalRes<FileRes>> storePosts(
        @ModelAttribute MultipartFile file
    ) {
        return ResponseEntity.ok(GlobalRes.success(fileService.storePosts(file)));
    }
}
```

> 파일 파라미터를 `@RequestParam` 대신 `@ModelAttribute`로 받는다 — 이후 업로드 요청에 필드가 늘어나도(예: 설명 텍스트) 컨트롤러 시그니처를 그대로 두고 요청 객체 쪽만 확장할 수 있다.

### 2-6. Gateway — 변경 없음

```yaml
# api-gateway/src/main/resources/application.yaml — 라우트 추가 불필요
routes:
  - id: auth-service
    uri: http://localhost:8081
    predicates:
      - Path=/api/auth/**   # /api/auth/files/profiles도 이미 포함됨

  - id: post-service
    uri: http://localhost:8082
    predicates:
      - Path=/api/posts/**  # /api/posts/files/images도 이미 포함됨
```

file-service 같은 별도 서비스를 만들지 않기로 했으므로, Gateway 설정은 [MSA 챕터](./01-msa-architecture.md)에서 만든 그대로 둔다 — 새 라우트도, 새 predicate도 필요 없다.

### 2-7. 프론트엔드 — 업로드 경로 수정

| 항목 | 변경 전 (v1) | 변경 후 |
|------|-------------|---------------------|
| 프로필 이미지 업로드 요청 | `POST /api/files/profiles` | `POST /api/auth/files/profiles` |
| 게시글 이미지 업로드 요청 | `POST /api/files/posts` | `POST /api/posts/files/images` |

`myAxios.js`의 `baseURL`은 이미 Gateway를 향하고 있으므로 그대로 두고, 파일 업로드를 호출하는 두 곳(회원가입 폼, 게시글 작성 폼)의 요청 경로 문자열만 위 표대로 수정한다. 이후 클라이언트 흐름(파일 먼저 업로드 → URL을 회원가입/게시글 작성 요청 본문에 재사용)은 v1과 동일하게 유지된다.

### 2-8. DB 컬럼 길이 재검토

`users.profile`은 이미 `VARCHAR(255)`로 확장돼 있고, `posts.image`는 아직 `VARCHAR(100)`이다. v1의 로컬 URL도 이미 86자 안팎(`http://localhost:8080/files/posts/20250101_550e8400-e29b-41d4-a716-446655440000.jpg`)이었는데, MinIO 영구 URL은 여기에 버킷명 + prefix 폴더가 붙는다.

```
http://{MinIO 엔드포인트 호스트}/meerkat/post/images/20250101_550e8400-e29b-41d4-a716-446655440000.jpg
```

엔드포인트 호스트명 길이에 따라 100자를 넘길 수 있다 — 실습 시 실제 MinIO 엔드포인트 값으로 길이를 재보고, 넘긴다면 컬럼을 `VARCHAR(255)` 등으로 넓히는 마이그레이션(`ALTER TABLE`)이 필요하다는 점을 확인한다.

> presigned URL을 택하지 않은 이유도 여기서 다시 확인할 수 있다 — presigned URL은 서명 쿼리스트링이 추가로 200자 이상 붙기 때문에 컬럼 확장 폭이 훨씬 커지고, 게다가 만료되므로 DB에 영구 저장하는 것 자체가 맞지 않는다.

### 2-9. 통합 테스트

```
# 1. 프로필 이미지 업로드 (auth-service 경유)
POST http://localhost:8080/api/auth/files/profiles
→ { "fileUri": "http://minio.internal:9000/meerkat/auth/profiles/20250101_uuid.png" }

# 2. 위 fileUri로 회원가입
POST http://localhost:8080/api/auth/registration
Body: { ..., "profile": "http://minio.internal:9000/meerkat/auth/profiles/20250101_uuid.png" }

# 3. 게시글 이미지 업로드 (post-service 경유)
POST http://localhost:8080/api/posts/files/images
→ { "fileUri": "http://minio.internal:9000/meerkat/post/images/20250101_uuid.jpg" }

# 4. 브라우저에서 fileUri로 직접 접근 → MinIO가 이미지를 직접 서빙하는지 확인
GET http://minio.internal:9000/meerkat/post/images/20250101_uuid.jpg
```

### 2강 정리
- 로컬 디스크 서빙 코드 → `MinioManager`(auth-service)/`MinioFileManager`(post-service): 같은 패턴을 각 서비스에 적용, 저장만 `PutObject`로 교체
- 버킷·자격증명은 공용 1개, `minioProfilePath`/`minioImagePath`(prefix)로만 서비스를 구분 — MinIO가 강제하는 접근 통제가 아니라 코드 상의 관례임을 인지하고 넘어간다
- 업로드 경로를 각 서비스 네임스페이스 하위로 이동(`/api/auth/files/profiles`, `/api/posts/files/images`) → Gateway는 변경 없음, 프론트 요청 경로만 수정
- Public 버킷 + 영구 URL 방식 채택 → `posts.image` 컬럼 길이를 실측 후 필요시 확장

---

## 이 챕터에서 구현한 것

- [x] MinIO 인프라 팀에 공용 버킷(`meerkat`) 및 공용 자격증명 발급 요청 (최초 1회, auth-service/post-service 동일 값)
- [x] `auth-service`: `MinioManager`/`MinioConfig`/`MyMinioClient` 추가, 로컬 디스크 서빙 코드 제거
- [x] `post-service`: `MinioFileManager`/`MinioConfig`/`MyMinioClient` 추가, 로컬 디스크 서빙 코드 제거
- [x] 업로드 경로 이동: `/api/files/profiles` → `/api/auth/files/profiles`, `/api/files/posts` → `/api/posts/files/images` (Gateway 설정 변경 없음)
- [x] 프론트엔드 파일 업로드 요청 경로 수정
- [ ] `posts.image`(`VARCHAR(100)`) — MinIO URL 길이 실측, 필요시 컬럼 확장 (`users.profile`은 이미 `VARCHAR(255)`로 확장됨)
- [ ] auth-service·post-service·api-gateway 통합 테스트 (파일 업로드 → URL 재사용 흐름 확인)

다음 챕터에서는 이메일 로그인에 카카오 소셜 로그인을 추가한다 → [카카오 소셜 로그인 챕터](./04-social-login-kakao.md)
