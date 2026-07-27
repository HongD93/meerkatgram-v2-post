# 1. MSA 전환

단일 Spring Boot 애플리케이션(v1.0)을 `api-gateway` + `auth-service` + `post-service`로 나눈다. 이 챕터는 **아키텍처 변화**가 핵심이며, 비즈니스 로직 자체는 거의 바뀌지 않는다.

---

## 1강. MSA 설계 — 서비스 경계 정의

### 학습 목표
- MA(Monolithic Architecture)와 MSA의 차이와 트레이드오프를 설명할 수 있다
- 어떤 기준으로 서비스를 나누는지 설명할 수 있다
- Meerkatgram의 서비스 분리 계획을 이해할 수 있다

### 1-1. MA(모놀리식)란?

v1.0 Meerkatgram은 **단일 Spring Boot 프로젝트**에 모든 기능이 들어있다.

```
msa4-meerkatgram-api/
    ├── domain/auth/
    ├── domain/user/
    ├── domain/post/
    └── domain/file/
```

하나의 JAR 파일로 빌드되고, 하나의 프로세스로 실행된다.

프로젝트가 커지면 이런 문제가 생긴다.

| 문제 | 상황 |
|------|------|
| **배포 위험** | 게시글 기능 수정 → 전체 서버 재시작 → 로그인도 잠깐 멈춤 |
| **장애 전파** | 파일 업로드 기능 오류 → 전체 서버 다운 → 로그인도 불가 |
| **확장 어려움** | 게시글 조회만 트래픽이 몰려도 전체 서버를 증설해야 함 |
| **기술 스택 고정** | 한 서비스만 Python으로 바꾸고 싶어도 전체가 Java |

### 1-2. MSA(마이크로서비스 아키텍처)란?

기능별로 독립적인 서비스로 분리하는 설계 방식이다.

```
Before (MA)                    After (MSA)
──────────────────────         ──────────────────────────────────
단일 서버 1개                   api-gateway + auth-service + post-service
모든 기능이 한 곳에              각 기능이 독립된 서버에
하나의 DB                       서비스별 독립 DB
```

**장점**
- 게시글 서비스 배포 시 인증 서비스는 계속 실행
- 게시글 조회만 서버를 늘릴 수 있음 (Scale Out)
- 팀별로 다른 기술 스택 선택 가능

**단점**
- 운영 복잡도 급증 (서버 3개 → 모니터링 3배)
- 서비스 간 네트워크 통신 추가 (실패 가능성)
- 개발 환경 셋업이 어려워짐

> 쉽게 말하면: 마트(모놀리식) vs 전문점 거리(MSA). 마트는 편하지만 한 곳이 문제면 다 멈춘다. 전문점 거리는 빵집이 쉬어도 과일가게는 계속 영업한다.

### 1-3. 서비스 분리 기준

무조건 잘게 나누는 게 좋은 MSA가 아니다. **적절한 경계**를 찾는 것이 핵심이다.

| 기준 | 설명 |
|------|------|
| **변경 이유가 다른가?** | 인증 로직 변경과 게시글 로직 변경은 서로 영향 없어야 함 |
| **독립적으로 배포 가능한가?** | auth 서비스 업데이트 시 post 서비스 재배포 불필요 |
| **DB를 독립적으로 가질 수 있는가?** | post 서비스가 users 테이블을 직접 조회하지 않아야 함 |

### 1-4. Meerkatgram v2.0 서비스 분리

| 서비스 | 포트 | 담당 기능 | 사용하는 테이블 |
|--------|------|---------|--------------|
| `api-gateway` | 8080 | 라우팅, JWT 검증, CORS | 없음 |
| `auth-service` | 8081 | 로그인, 회원가입, 재발급, 유저 조회, 소셜로그인, 프로필 이미지 업로드 | `users` |
| `post-service` | 8082 | 게시글 CRUD, 게시글 이미지 업로드 | `posts` |

> **왜 user-service를 분리하지 않았나?** 현재 유저 기능(조회)이 단순하고 auth와 밀접하게 연관되어 있어 분리 실익이 낮다. 이후 팔로우, DM 등 유저 기능이 복잡해지면 분리를 고려한다.

> **왜 file-service를 분리하지 않았나?** 파일 업로드/서빙만 담당하는 서비스로 쪼개봐야 코드 중복만 없어질 뿐, MSA의 실질적 이득(독립 스케일링, 장애 격리)은 크지 않다. 대신 파일 소유권을 도메인별로 나눈다 — 프로필 이미지는 auth-service(`/api/auth/files/profiles`), 게시글 이미지는 post-service가 각자 관리한다. 저장소를 MinIO로 옮긴 이후에도 이 결정은 유지된다(도메인 소유권은 그대로, 저장 방식만 바뀐다) — 자세한 내용은 [파일 스토리지 챕터](./03-minio-file-storage.md) 참고.

### 1-5. 서비스 간 DB 격리와 클라이언트 조인

```
auth-service  →  auth_db  →  users 테이블
post-service  →  post_db  →  posts 테이블
```

이 프로젝트는 **하위 서비스(auth-service ↔ post-service)끼리는 서로 통신하지 않는다**는 원칙을 둔다 — post-service가 auth-service를 직접 호출하거나, Gateway가 두 서비스의 데이터를 미리 합쳐 넘겨주는 방식 모두 이 원칙에 어긋난다.

post-service가 게시글 작성자 정보(닉네임, 프로필)가 필요해도 `users` 테이블은 auth-service 소유이므로 직접 조회할 수 없다. 대신 **post-service는 `userId`만 반환하고, 작성자 정보는 프론트엔드가 auth-service를 별도로 호출해 클라이언트에서 조합**하는 클라이언트 조인 방식을 쓰기로 했다 — 다만 이를 위한 auth-service의 유저 다건 조회 API는 아직 구현 전이다. 설계 배경과 다른 방법과의 비교는 [05-planned-features.md](../05-planned-features.md) 참고.

### 1강 정리
- MA: 단일 프로세스, 빠른 개발 / MSA: 서비스 분리, 독립 배포
- 서비스 경계: "변경 이유가 다른가", "독립 배포 가능한가"
- Meerkatgram: api-gateway + auth-service + post-service (3개 서버), DB도 서비스별로 분리(auth_db, post_db)

---

## 2강. API Gateway 구축 — 라우팅과 JWT 검증

### 학습 목표
- `api-gateway` 프로젝트를 생성하고 라우팅을 설정할 수 있다
- JWT 검증 필터를 구현하고, 검증 후 사용자 정보를 헤더로 전달할 수 있다
- 인증(Authentication)은 Gateway가, 인가(Authorization)는 하위 서비스가 맡아야 하는 이유를 설명할 수 있다

### 2-1. api-gateway 프로젝트 생성

Spring Initializr(start.spring.io)에서 새 프로젝트 생성:

| 항목 | 값 |
|------|-----|
| Project | Gradle |
| Language | Java 17 |
| Spring Boot | 4.1 |
| Dependencies | Spring Cloud Gateway (Reactive), Lombok |

> **주의:** Spring Cloud Gateway는 WebFlux(리액티브) 기반이다. auth-service/post-service는 Servlet 기반이지만 Gateway만 WebFlux를 사용한다 — 코드 스타일이 조금 다를 수 있다.
>
> 쉽게 말하면: API Gateway는 아파트 경비원이다. 외부 방문객(요청)을 확인하고 맞는 동(서비스)으로 안내한다.

### 2-2. build.gradle

```groovy
// api-gateway/build.gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com'
version = 'v1'
description = 'msa-meerkatgram-v2-scg'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

ext {
    set('springCloudVersion', "2025.1.2")
}

dependencies {
    // SCG
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway-server-webflux'
    testImplementation 'io.projectreactor:reactor-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'

    // SpringDoc Openapi (Swagger)
    implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.3'

    // jjwt
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

    // Lombok
    implementation 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'

    // Configuration
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}

tasks.named('test') {
    useJUnitPlatform()
}
```

> `spring-cloud-starter-gateway-server-webflux`를 쓰기 때문에 2-3의 `application.yaml`도 `spring.cloud.gateway.server.webflux.*`로 중첩된다. `springdoc-openapi-starter-webflux-ui`도 WebFlux 전용 스타터를 써야 한다 — webmvc용 스타터를 넣으면 Swagger 통합이 제대로 뜨지 않는다.

### 2-3. application.yaml — 라우팅 설정

```yaml
# api-gateway/src/main/resources/application.yaml
server:
  port: 8080

jwt:
  secret: ${JWT_SECRET}
  header-key: Authorization
  scheme: Bearer

spring:
  cloud:
    gateway:
      server:
        webflux:
          default-filters:
            - name: RequestSize
              args:
                maxSize: 20MB
          globalcors:
            cors-configurations:
              '[/**]':
                allowed-origins: "http://localhost:5173"
                allowed-methods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]
                allowed-headers: "*"
                allow-credentials: true
          routes:
            # AuthFilter는 GlobalFilter라 별도 filters: 선언 없이 모든 라우트에 자동 적용된다.
            - id: auth-service
              uri: http://localhost:8081
              predicates:
                - Path=/api/auth/**

            - id: post-service
              uri: http://localhost:8082
              predicates:
                - Path=/api/posts/**

            # Swagger UI가 각 서비스 스펙을 게이트웨이 자기 origin(상대경로)으로 받아오도록 프록시
            - id: auth-service-docs
              uri: http://localhost:8081
              predicates:
                - Path=/auth-service/api-docs
              filters:
                - RewritePath=/auth-service/api-docs, /api-docs

            - id: post-service-docs
              uri: http://localhost:8082
              predicates:
                - Path=/post-service/api-docs
              filters:
                - RewritePath=/post-service/api-docs, /api-docs

# Springdoc Openapi — 하위 서비스 스펙을 게이트웨이 하나에서 모아 보여준다
springdoc:
  swagger-ui:
    path: /swagger.html
    urls:
      - name: auth-service
        url: /auth-service/api-docs
      - name: post-service
        url: /post-service/api-docs
```

> 서비스당 라우트가 하나뿐이다 — 로그인/비로그인 경로 구분이 필요 없으므로 `/api/auth/**`처럼 서비스 전체를 한 번에 잡아도 된다. 새 엔드포인트가 공개든 비공개든 라우팅 설정을 다시 만질 필요가 없다(2-4 참고).
>
> `RequestSize`는 하위 서비스들의 `spring.servlet.multipart.max-request-size`(auth-service 기준 20MB)와 맞춰뒀다. WebFlux 게이트웨이에는 Servlet 전용인 `spring.servlet.multipart.*`가 적용되지 않으므로, 요청 크기 제한은 이 필터로 건다.
>
> `auth-service-docs`/`post-service-docs`는 Swagger UI 전용 라우트다. `springdoc.swagger-ui.urls`에 상대경로로 등록해두면 브라우저는 게이트웨이(:8080)에만 요청을 보내고, 실제 프록시는 게이트웨이가 서버 쪽에서 처리한다 — 그래서 auth-service/post-service에 별도 CORS 설정을 열어줄 필요가 없다.
>
> 위 yaml은 값을 그대로 하드코딩해 보여준 것이다 — 실제로는 `uri`, `predicates`, `allowed-origins` 등을 `.env` 기반 환경 변수로 뺀다. 라우팅 predicate도 실제로는 서비스당 `/api/auth/**`와 `/auth/**`처럼 패턴이 두 개씩 들어있을 수 있다 — 여기서는 핵심 구조를 보여주려고 최소 형태로 단순화했다.

### 2-4. AuthFilter — JWT 검증 필터 구현

Gateway는 auth-service/post-service처럼 `GlobalRes`를 가져다 쓸 수 없다 — 별도 WebFlux 프로젝트라 공유 모듈이 없기 때문이다. 3개 프로젝트를 위한 공유 라이브러리까지 만들 필요는 없으니, Gateway 자신의 `global/responses/`에 auth-service와 동일한 모양의 `GlobalRes`/`CustomResponseCode`를 복제해서 둔다.

```java
// gateway/global/responses/constant/CustomResponseCode.java
@Getter
public enum CustomResponseCode {
    INVALID_TOKEN_ERROR(HttpStatus.UNAUTHORIZED, "E04")
    , NOT_FOUND_ERROR(HttpStatus.NOT_FOUND, "E50")
    , SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E99")
    ;

    private final HttpStatus httpStatus;
    private final String code;

    CustomResponseCode(HttpStatus httpStatus, String code) {
        this.httpStatus = httpStatus;
        this.code = code;
    }
}
```

```java
// gateway/global/responses/GlobalRes.java
public record GlobalRes<T>(String code, String message, T data) {
    public static GlobalRes<Void> from(CustomResponseCode customResponseCode) {
        return new GlobalRes<Void>(customResponseCode.getCode(), customResponseCode.name(), null);
    }
}
```

> **왜 `GatewayFilter`가 아니라 `GlobalFilter`인가?** YAML의 `filters:`(또는 `default-filters:`)에 이름으로 걸 수 있는 건 `GatewayFilterFactory`로 등록된 빈뿐이다. `GlobalFilter`는 스프링 빈으로 등록되는 순간 YAML 선언 없이 모든 라우트에 자동 적용되므로, "모든 라우트에 예외 없이 걸려야 한다"는 요구사항에 맞다.

```java
// global/filter/AuthFilter.java
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {
    private final ObjectMapper objectMapper;
    private final JwtProvider jwtProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        try {
            // Authorization 헤더에서 토큰 추출 — JwtProvider가 헤더 파싱까지 함께 맡는다
            Optional<String> token = jwtProvider.extractAccessToken(exchange);

            // 토큰이 아예 없으면 익명 요청으로 그대로 통과시킨다.
            // 이 경로가 로그인이 필요한지는 게이트웨이가 알 필요가 없다 — 하위 서비스가 판단한다.
            if (token.isEmpty()) {
                return chain.filter(exchange);
            }

            // JWT 검증 (auth-service와 동일한 비밀키 사용)
            Claims claims = jwtProvider.extractClaims(token.get());

            // 검증된 정보를 헤더에 담아 하위 서비스로 전달
            // 원본 Authorization은 제거 — 검증이 끝난 토큰이 하위 서비스까지 흘러갈 이유가 없다
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(httpHeaders -> httpHeaders.remove("Authorization"))
                .header("X-User-Id",   claims.getSubject())
                .header("X-User-Role", claims.get("role", String.class))
                .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (InvalidTokenException e) {
            return unauthorized(exchange);
        }
    }

    @Override
    public int getOrder() {
        return -1;  // 가장 먼저 실행
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(CustomResponseCode.INVALID_TOKEN_ERROR.getHttpStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes = objectMapper.writeValueAsBytes(GlobalRes.from(CustomResponseCode.INVALID_TOKEN_ERROR));

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
```

> **왜 `@RestControllerAdvice`를 안 쓰는가?** Gateway는 라우팅/프록시를 애노테이션 컨트롤러 없이 `GlobalFilter`/`GatewayFilter`가 직접 `ServerWebExchange`를 다루는 함수형 파이프라인으로 처리한다. `@RestControllerAdvice`는 컨트롤러 메서드 호출 과정에서 던져진 예외를 가로채는 매커니즘이라, 컨트롤러 디스패치 자체가 없는 이 경로에는 개입할 지점이 없다. `AuthFilter`가 예상하는 예외(토큰은 있는데 무효한 경우)는 필터 안에서 직접 처리하고, 그 외 예외는 2-5 참고.

> **왜 게이트웨이가 경로별로 인증 여부를 판단하지 않는가?** "이 경로는 로그인이 필요한가"를 아는 건 사실 각 하위 서비스뿐이다. 여러 팀이 각자 서비스를 독립적으로 배포하는 상황에서, 게이트웨이가 경로 목록까지 관리하면 새 엔드포인트가 생길 때마다 게이트웨이 설정도 같이 갱신해야 하고, 이 동기화는 드리프트 나기 쉽다(깜빡하면 정상 요청이 401로 막히거나, 반대로 보호돼야 할 엔드포인트가 뚫린다).
>
> 그래서 게이트웨이는 "토큰이 있으면 검증해서 헤더로 넘기고, 없으면 그냥 통과시킨다"까지만 하고, "이 요청이 실제로 허용되는가"는 전적으로 하위 서비스가 판단한다. 각 서비스의 `SecurityConfiguration`은 URL 레벨에서 `permitAll()`로 다 열어두고(default-allow), 보호는 `@PreAuthorize`에만 의존한다 — 이 부분은 [권한 챕터](./02-role-permission.md)에서 자세히 다룬다.

> **원본 `Authorization` 헤더를 왜 제거하는가?** Spring Cloud Gateway는 hop-by-hop 헤더(`Connection`, `Proxy-Authorization` 등)만 기본으로 제거하고 `Authorization`은 건드리지 않는다. 그대로 두면 검증이 끝난 원본 JWT가 하위 서비스까지 불필요하게 흘러가서, 로그에 노출되거나 실수로 재사용될 여지를 남긴다. 하위 서비스는 어차피 `X-User-Id`/`X-User-Role`만 신뢰하고 JWT를 직접 파싱하지 않으므로, 검증이 끝난 시점에 Gateway에서 걷어내는 것이 안전하다.

### 2-5. 전역 예외 처리 — WebExceptionHandler

`AuthFilter`가 처리하는 건 자기가 예상한 예외(토큰 없음/무효)뿐이다. 그 바깥(라우팅 매칭 실패, 다운스트림 서비스 다운·타임아웃)은 Spring Boot 기본 `DefaultErrorWebExceptionHandler`가 대신 응답하는데, 이건 `GlobalRes`가 아니라 `timestamp`/`status`/`error`/`path` 형태의 Spring 기본 에러 포맷이라 응답 모양이 달라진다.

`WebExceptionHandler`(WebFlux에서 `@RestControllerAdvice`에 해당하는 전역 처리 지점)를 하나 등록해서 응답 모양을 통일한다.

```java
// global/error/GlobalErrorWebExceptionHandler.java
@Component
@Order(-2)  // Spring 기본 ErrorWebExceptionHandler(-1)보다 먼저 실행
@RequiredArgsConstructor
public class GlobalErrorWebExceptionHandler implements WebExceptionHandler {
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        CustomResponseCode code = (ex instanceof ResponseStatusException rse
            && rse.getStatusCode().value() == 404)
            ? CustomResponseCode.NOT_FOUND_ERROR   // 라우팅 매칭 실패
            : CustomResponseCode.SYSTEM_ERROR;     // 다운스트림 연결 실패 등

        response.setStatusCode(code.getHttpStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes = objectMapper.writeValueAsBytes(GlobalRes.from(code));
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
```

> `AuthFilter.unauthorized()`와 이 핸들러가 같은 `GlobalRes` record를 함께 쓴다 — 필터가 예상하는 예외는 필터 안에서, 그 외 전부는 이 핸들러 하나로 처리해 auth-service/post-service와 동일한 응답 모양을 유지한다.

### 2강 정리

| 항목 | v1.0 (모놀리식) | v2.0 (Gateway) |
|------|----------------|---------------|
| JWT 검증 위치 | 각 서비스의 `TokenAuthenticationFilter` | `api-gateway`의 `AuthFilter` |
| CORS 설정 | 각 서비스의 `CorsConfig` | `api-gateway`의 `application.yaml` |
| 인증 후 처리 | `@AuthenticationPrincipal Claims` | `Authentication` (각 서비스의 `HeaderAuthenticationFilter`가 채움) |

- `api-gateway` = 별도 Spring Boot 프로젝트 (WebFlux 기반)
- `AuthFilter`(`GlobalFilter`): 토큰이 있으면 검증 후 X-User-Id/X-User-Role 헤더 주입, 없으면 익명으로 통과 — **인증만** 하고 **인가는 하지 않는다**
- `GlobalErrorWebExceptionHandler`: 라우팅 실패·다운스트림 장애 등 그 외 예외를 `GlobalRes` 형태로 통일
- CORS, JWT 검증은 Gateway 한 곳에서 집중 관리

---

## 3강. auth-service 분리

### 학습 목표
- 모놀리식 코드에서 auth 도메인 코드를 분리해 독립 서비스로 만들 수 있다
- 분리 후 기존 인증 API가 정상 동작함을 확인할 수 있다

### 3-1. auth-service 프로젝트 구조

v1.0 모놀리식에서 아래 패키지를 복사한다.

```
auth-service/src/main/java/com/msameerkatgramv2auth/
│
├── MsaMeerkatgramV2AuthApplication.java   ← 진입점 (패키지명만 변경)
│
├── domain/
│   ├── auth/                      ← v1.0 domain/auth/ 그대로
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/            ← AuthRepository(JPA) — User 엔티티도 여기서 조회
│   │   ├── request/
│   │   └── response/
│   ├── user/                      ← User 엔티티 + 응답 DTO만 (별도 컨트롤러/서비스는 아직 없음)
│   │   ├── entity/
│   │   └── response/
│   └── file/                      ← 프로필 이미지 업로드
│       ├── controller/
│       └── service/
│
└── global/                        ← v1.0 global/에서 필요한 것만 복사
    ├── error/
    ├── response/
    ├── security/                  ← Spring Security 설정 (TokenAuthenticationFilter 제거)
    └── minio/                     ← 프로필 이미지 업로드용 (파일 스토리지 챕터 참고)
```

JWT 검증이 Gateway로 이동했으므로, auth-service에서는 `TokenAuthenticationFilter.java`(JWT 직접 검증)와 `CorsConfig.java`(Gateway가 처리)를 제거한다.

> `TokenAuthenticationFilter`가 하던 일(요청을 가로채 `SecurityContext`에 인증 정보를 채우는 것) 자체가 사라지는 건 아니다. JWT를 직접 파싱하던 부분만 Gateway로 옮겨가고, auth-service에는 Gateway가 주입한 헤더로 같은 일을 하는 새 필터가 들어선다.

### 3-2. auth-service SecurityConfiguration — 인증 소스 교체

JWT를 직접 검증하던 필터는 사라지지만, "필터가 `SecurityContext`를 채운다"는 Spring Security의 기본 골격은 v1.0과 동일하게 유지한다. 대신 필터가 읽는 대상이 `Authorization` 헤더(JWT)가 아니라 Gateway가 검증을 마치고 넣어준 `X-User-Id`/`X-User-Role` 헤더로 바뀐다.

```java
// auth-service HeaderAuthenticationFilter.java
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain
    ) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Role");

        // Gateway를 거치지 않고 직접 호출된 경우 헤더가 없을 수 있음 → 익명 상태로 통과
        if (StringUtils.isNotBlank(userId) && StringUtils.isNotBlank(role)) {
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        chain.doFilter(request, response);
    }
}
```

```java
// auth-service SecurityConfiguration.java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {
    private final HeaderAuthenticationFilter headerAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // 인증 여부와 무관하게 요청은 통과시키고, 실제 인가는 @PreAuthorize가 담당
            .authorizeHttpRequests(req -> req.anyRequest().permitAll())
            .build();
    }
}
```

> **왜 `permitAll()`인데 필터를 두나?** `authorizeHttpRequests`는 URL 단위 인가만 담당한다. 메서드 단위 인가(`@PreAuthorize`)가 검사할 `Authentication`/`GrantedAuthority`는 여전히 `SecurityContext`에 있어야 하므로, 그걸 채우는 필터는 필요하다. `@PreAuthorize`를 어떻게 조합해서 실제 권한 체계를 만드는지는 [권한 챕터](./02-role-permission.md)에서 다룬다.

### 3-3. auth-service Controller — Authentication 기반 처리

컨트롤러는 v1.0과 거의 같은 방식으로 유저를 식별한다 — `Authentication`을 필터가 채워주기 때문이다.

```java
// AuthController.java — 로그아웃 예시 (클래스 레벨 @RequestMapping("/api/auth") 적용됨)
@PreAuthorize("isAuthenticated()")   // 특정 role은 필요 없지만, 로그인은 필요하다
@PostMapping("/logout")
public ResponseEntity<GlobalResponseDTO<Void>> logout(
    HttpServletResponse response,
    Authentication authentication   // HeaderAuthenticationFilter가 채워준 인증 정보
) {
    long userId = Long.parseLong(authentication.getName());
    authService.logout(response, userId);   // 쿠키의 refreshToken도 함께 제거
    return ResponseEntity.ok(GlobalResponseDTO.success());
}
```

**Before (v1.0 — JWT를 직접 검증하는 필터가 SecurityContext를 채움)**
```java
public ResponseEntity<?> logout(@AuthenticationPrincipal Claims claims) {
    long userId = Long.parseLong(claims.getSubject());
    // ...
}
```

**After (v2.0 — Gateway 헤더를 읽는 필터가 SecurityContext를 채움)**
```java
public ResponseEntity<?> logout(Authentication authentication) {
    long userId = Long.parseLong(authentication.getName());
    // ...
}
```

바뀐 건 "누가 검증하는가"(auth-service 자체 → Gateway)와 "필터가 어디서 값을 읽는가"(JWT → 헤더)뿐이다. 컨트롤러가 `Authentication`으로 유저를 식별하는 방식 자체는 그대로다.

> **`@PreAuthorize`가 없으면 어떻게 되나:** 익명 요청은 헤더가 없어 `SecurityContext`가 안 채워지지만, Spring Security가 기본으로 `AnonymousAuthenticationToken`(principal: `"anonymousUser"`)을 채워 넣는다. `@PreAuthorize` 없이 그대로 두면 `Long.parseLong("anonymousUser")`에서 `NumberFormatException`이 터져 401이 아니라 500이 나간다. 게이트웨이가 이 경로를 막아주지 않으므로(2-4), role 검사가 필요 없는 "로그인만 하면 되는" 엔드포인트에도 `@PreAuthorize("isAuthenticated()")`를 명시적으로 붙여야 한다.

### 3-4. auth-service application.yaml

```yaml
# auth-service/src/main/resources/application.yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/auth_db   # ← 별도 DB 스키마
    username: root
    password: 1234
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true

jwt:
  secret: ${JWT_SECRET}   # api-gateway와 동일한 값
  issuer: meerkatgram

springdoc:
  swagger-ui:
    enabled: false   # 자체 UI는 끄고 스펙(JSON) 생성만 유지 — 게이트웨이가 모아서 보여준다
  open-api:
    servers:
      - url: ${GATEWAY_URI}   # auth-service 자기 주소가 아니라 게이트웨이 주소
        description: ${APP_DESCRIPTION}
```

> `open-api.servers`가 auth-service 자기 주소로 남아있으면, Swagger UI의 "Try it out"이 게이트웨이를 거치지 않고 auth-service로 직접 요청을 보내면서 `AuthFilter`의 토큰 검증·헤더 변환을 안 타게 되고, CORS 에러도 함께 난다. 그래서 게이트웨이 주소로 바꿔야 한다.
>
> `datasource.url`/`username`/`password`도 실제로는 `.env` 기반 환경 변수로 관리된다 — 하드코딩된 값은 이해를 돕기 위한 예시다.

### 3-5. auth-service 단독 테스트

Gateway 없이 auth-service만 실행해 아래를 확인한다.

| API | 테스트 방법 |
|-----|-----------|
| `POST http://localhost:8081/api/auth/registration` | 회원가입 정상 동작 |
| `POST http://localhost:8081/api/auth/login` | 로그인 + accessToken 발급 |
| `POST http://localhost:8081/api/auth/logout` (X-User-Id 헤더 포함) | 200 정상 로그아웃 |
| `POST http://localhost:8081/api/auth/logout` (헤더 없이, 익명) | **401** — `@PreAuthorize("isAuthenticated()")`가 막는지 확인하는 안전망 테스트 |

### 3강 정리
- auth-service = v1.0 auth + user 도메인 패키지 복사 후 Security 설정 조정
- `TokenAuthenticationFilter`(JWT 직접 검증) 제거 → `HeaderAuthenticationFilter`(Gateway 헤더 기반)로 교체
- `@AuthenticationPrincipal Claims` → `Authentication`으로 변경 (SecurityContext를 채우는 패턴은 유지)
- `application.yaml`의 포트를 8081로, DB를 auth_db로 설정

---

## 4강. post-service 분리와 서비스 자율성

### 학습 목표
- post 도메인 코드를 분리해 독립 서비스로 만들 수 있다
- 서비스가 자기 데이터만 소유·반환하고, 다른 서비스 데이터와의 조합은 클라이언트가 맡아야 하는 이유를 설명할 수 있다

### 4-1. post-service 프로젝트 구조

```
post-service/src/main/java/com/msameerkatgramv2post/
│
├── MsaMeerkatgramV2PostApplication.java
│
├── domain/
│   ├── post/                      ← v1.0 domain/post/ 그대로
│   │   ├── controllers/
│   │   ├── entities/
│   │   ├── repositories/
│   │   ├── requests/
│   │   ├── responses/
│   │   └── services/
│   └── file/                      ← v1.0 domain/file/ 그대로
│       ├── controllers/
│       └── services/
│
└── global/                        ← v1.0 global/에서 공통 코드만 복사
    ├── errors/
    ├── responses/
    └── util/file/
```

auth-service와 마찬가지로 `global/security/` 관련 코드(`TokenAuthenticationFilter`, `CorsConfig`)는 제거한다.

### 4-2. post-service SecurityConfiguration

auth-service(3-2)와 동일한 `HeaderAuthenticationFilter`를 그대로 가져와 쓴다 — Gateway가 검증하고 헤더로 넘겨준 인증 정보를 `SecurityContext`에 채우는 역할은 어느 서비스에서든 동일하기 때문이다.

```java
// post-service SecurityConfiguration.java — auth-service와 동일 패턴
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .csrf(AbstractHttpConfigurer::disable)
        .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(req -> req.anyRequest().permitAll())
        .build();
}
```

컨트롤러도 auth-service와 동일하게 `Authentication` 파라미터로 유저를 식별한다. 게시글 작성/삭제에 필요한 `@PreAuthorize("hasRole('SUPER')")` 구현과 테스트는 [권한 챕터](./02-role-permission.md)에서 이어서 다룬다.

### 4-3. post-service application.yaml

```yaml
# post-service/src/main/resources/application.yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/post_db   # ← 별도 DB 스키마
    username: root
    password: 1234
  jpa:
    hibernate:
      ddl-auto: validate
```

> 위 값들도 실제로는 `.env` 기반 환경 변수로 관리된다 — 하드코딩된 값은 이해를 돕기 위한 예시다.

### 4-4. 작성자 정보는 누가, 언제 채우나 — 클라이언트 조인 (계획)

post-service가 게시글 목록을 반환할 때 `작성자 닉네임`, `프로필 이미지`가 필요한 경우를 생각해보자. 이 정보는 `users` 테이블에 있고, `users` 테이블은 auth-service가 소유한다.

post-service는 게시글 응답에 `userId`만 담아 반환하고, 닉네임/프로필은 프론트가 auth-service를 별도로 호출해 화면에서 조합하는 **클라이언트 조인** 방식을 쓰기로 했다 — 다른 방법(Gateway 헤더 활용, 서비스 간 직접 호출, 데이터 비정규화)과의 비교, 그리고 이를 위한 auth-service의 유저 다건 조회 API는 아직 구현 전이라 [05-planned-features.md](../05-planned-features.md)에 정리해뒀다.

### 4-5. 3개 서비스 통합 테스트

모든 서비스를 동시에 실행한 후 Gateway를 통해 테스트한다.

| 실행 순서 | 서비스 | 포트 |
|----------|--------|------|
| 1 | auth-service | 8081 |
| 2 | post-service | 8082 |
| 3 | api-gateway  | 8080 |

```
# Postman에서 Gateway 주소(8080)로만 테스트
POST http://localhost:8080/api/auth/login     → auth-service:8081로 라우팅
GET  http://localhost:8080/api/posts          → post-service:8082으로 라우팅
```

> 클라이언트 조인 흐름(게시글 목록 + 유저 다건 조회 API)은 아직 구현 전이라 테스트할 수 없다 — 구현 후 확인할 테스트 흐름은 [05-planned-features.md](../05-planned-features.md)에 정리해뒀다.

### 4강 정리
- post-service: v1.0 post + file 도메인 복사, Security 설정은 auth-service와 동일한 `HeaderAuthenticationFilter` 패턴 재사용
- 작성자 닉네임/프로필: post-service는 `userId`만 반환하고, 프론트 클라이언트 조인으로 해결할 계획 — 이를 위한 auth-service의 유저 다건 조회 API는 아직 미구현([05-planned-features.md](../05-planned-features.md) 참고)
- 3개 서비스 동시 실행 후 Gateway(8080)로 통합 테스트

---

## 5강. Docker 컨테이너화 및 k8s 배포 연결

### 학습 목표
- 각 서비스를 Docker 이미지로 빌드할 수 있다
- `docker-compose`로 전체 환경을 한 번에 실행할 수 있다
- k8s 배포와의 연결 고리를 이해한다

### 5-1. 왜 Docker인가?

```
개발 PC (Java 17, MySQL 8.4)
배포 서버 (Java 11, MySQL 5.7)

→ "내 컴에서는 됐는데요" 문제 발생
```

Docker는 **실행 환경 자체를 이미지로 패키징**한다. 개발 PC, CI 서버, 프로덕션 서버 어디서나 동일하게 실행된다.

> 쉽게 말하면: 이 프로그램을 실행하는 데 필요한 환경까지 박스에 담아 배달하는 것이다.

### 5-2. Dockerfile 작성

모든 서비스의 Dockerfile 구조는 동일하다.

```dockerfile
# auth-service/Dockerfile (post-service, api-gateway도 동일 구조)
FROM eclipse-temurin:17-jre-alpine    # ① 가벼운 Java 17 런타임

WORKDIR /app

# ② Gradle로 빌드된 JAR 파일 복사
COPY build/libs/*.jar app.jar

# ③ 환경 변수로 DB 접속 정보, JWT 비밀키 주입 (하드코딩 금지)
ENV SPRING_DATASOURCE_URL=jdbc:mysql://auth-db:3306/auth_db
ENV JWT_SECRET=changeme

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# 1. 각 서비스 JAR 빌드
cd auth-service && ./gradlew bootJar
cd post-service && ./gradlew bootJar
cd api-gateway  && ./gradlew bootJar

# 2. Docker 이미지 빌드
docker build -t meerkatgram/auth-service:v2.0 ./auth-service
docker build -t meerkatgram/post-service:v2.0 ./post-service
docker build -t meerkatgram/api-gateway:v2.0  ./api-gateway
```

### 5-3. docker-compose.yml 작성

개발 환경에서 전체 시스템을 한 번에 실행하기 위한 설정이다.

> 쉽게 말하면: docker-compose는 박스 여러 개를 한 번에 순서대로 열어주는 지시서다.

```yaml
# docker-compose.yml (프로젝트 루트)
version: '3.8'

services:
  # ── DB ──────────────────────────────
  auth-db:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: auth_db
      MYSQL_ROOT_PASSWORD: 1234
    ports:
      - "3306:3306"
    volumes:
      - auth-db-data:/var/lib/mysql

  post-db:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: post_db
      MYSQL_ROOT_PASSWORD: 1234
    ports:
      - "3307:3306"    # 호스트 포트 충돌 방지
    volumes:
      - post-db-data:/var/lib/mysql

  # ── Services ────────────────────────
  auth-service:
    image: meerkatgram/auth-service:v2.0
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://auth-db:3306/auth_db
      JWT_SECRET: ${JWT_SECRET}
    ports:
      - "8081:8081"
    depends_on:
      - auth-db

  post-service:
    image: meerkatgram/post-service:v2.0
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://post-db:3306/post_db
      JWT_SECRET: ${JWT_SECRET}
    ports:
      - "8082:8082"
    depends_on:
      - post-db

  api-gateway:
    image: meerkatgram/api-gateway:v2.0
    environment:
      JWT_SECRET: ${JWT_SECRET}
      AUTH_SERVICE_URL: http://auth-service:8081
      POST_SERVICE_URL: http://post-service:8082
    ports:
      - "8080:8080"
    depends_on:
      - auth-service
      - post-service

volumes:
  auth-db-data:
  post-db-data:
```

```bash
# 전체 실행
docker-compose up -d

# 전체 종료
docker-compose down
```

### 5-4. k8s와의 연결 고리

```
Docker                           k8s
──────────────────────────────   ──────────────────────────────
이미지 빌드                  →   이미지를 가져와 컨테이너 실행
docker-compose (로컬)        →   Deployment (프로덕션)
수동 스케일링                →   자동 스케일링 (HPA)
단일 서버                    →   클러스터 (여러 노드)
```

**k8s 기본 매니페스트 구조 (개념만 확인)**

```yaml
# auth-service-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
spec:
  replicas: 2          # auth-service를 2개 실행 (HA)
  selector:
    matchLabels:
      app: auth-service
  template:
    metadata:
      labels:
        app: auth-service
    spec:
      containers:
        - name: auth-service
          image: meerkatgram/auth-service:v2.0
          ports:
            - containerPort: 8081
          env:
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:        # k8s Secret에서 주입
                  name: jwt-secret
                  key: secret
---
apiVersion: v1
kind: Service
metadata:
  name: auth-service
spec:
  selector:
    app: auth-service
  ports:
    - port: 8081
      targetPort: 8081
```

> 쉽게 말하면: k8s는 박스(컨테이너)들이 쓰러지면 자동으로 일으켜 세우고, 손님이 몰리면 자동으로 박스를 더 가져다 놓는 관리 시스템이다. `replicas: 2`는 auth-service를 2개 동시에 켜두어 하나가 죽어도 서비스가 유지되게 하는 설정이다.
>
> k8s 상세 배포 실습(클러스터 구성, 네트워킹 등)은 이 교재의 범위를 벗어난다 — 여기서는 "Docker 이미지 → k8s Deployment"로 이어지는 연결 구조만 이해하고 넘어간다.

### 5-5. 환경 변수 관리 전략

| 환경 | 비밀키 관리 방법 |
|------|--------------|
| 로컬 개발 | `.env` 파일 (git 제외) |
| docker-compose | `.env` 파일 또는 shell 환경 변수 |
| k8s | `Secret` 리소스 (base64 인코딩, etcd 암호화) |
| CI/CD | CI 도구의 Credentials 저장소 |

```bash
# .env 파일 예시 (git에 올리면 안 됨)
JWT_SECRET=my-super-secret-key-256-bit-minimum
KAKAO_CLIENT_ID=abc123def456
KAKAO_CLIENT_SECRET=xyz789
```

### 5강 정리

**신규 프로젝트:**

| 프로젝트 | 역할 |
|-------------|------|
| `api-gateway` | SCG 라우팅, JWT 검증, CORS, 헤더 주입 |
| `auth-service` | 인증 도메인 독립 서비스 (포트 8081) |
| `post-service` | 게시글 도메인 독립 서비스 (포트 8082) |

**주요 코드 변화 패턴:**

```
모놀리식 → 분리 후
─────────────────────────────────────────────────
@AuthenticationPrincipal Claims   → Authentication (SecurityContext를 채우는 필터의 대상만 교체)
CorsConfig (각 서비스)             → Gateway application.yaml
TokenAuthenticationFilter (각 서비스, JWT 직접 검증) → HeaderAuthenticationFilter(각 서비스, Gateway 헤더 신뢰) + AuthFilter(Gateway, JWT 검증)
단일 MySQL DB                      → auth_db + post_db
JAR 1개                            → Docker 이미지 3개
로컬 실행                          → docker-compose → k8s
```

---

## 이 챕터에서 구현한 것

- [ ] `api-gateway` 프로젝트 생성, 라우팅 설정, `AuthFilter`/`GlobalErrorWebExceptionHandler` 구현
- [ ] `auth-service` 프로젝트 생성 — `HeaderAuthenticationFilter` 기반 Security 설정
- [ ] `post-service` 프로젝트 생성 — `HeaderAuthenticationFilter` 기반 Security 설정
- [ ] auth-service의 유저 다건 조회 API(`GET /api/auth/users?ids=`) 구현, 클라이언트 조인 확인 — 설계는 [05-planned-features.md](../05-planned-features.md) 참고
- [ ] 각 서비스 `Dockerfile` 작성, `docker-compose.yml` 작성 및 통합 테스트

다음 챕터에서는 이 Gateway + post-service 구조 위에서 **게시글 삭제 기능과 권한 체계**를 완성한다 → [권한(인가) 챕터](./02-role-permission.md)
