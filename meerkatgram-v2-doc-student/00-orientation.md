# 0. 오리엔테이션 — v1.0에서 v2.0으로

## 이 교재의 목표

Meerkatgram v1.0(모놀리식 Spring Boot 애플리케이션)을 v2.0(MSA + 소셜 로그인 + 오브젝트 스토리지)으로 발전시키는 과정을 통해 아래 기술을 익힌다.

- **MSA(마이크로서비스 아키텍처)**: 서비스 경계 설계, API Gateway, 서비스 자율성
- **인가(Authorization)**: Spring Security 권한 체계, `@PreAuthorize`
- **OAuth2**: 소셜 로그인(카카오) 연동
- **오브젝트 스토리지**: MinIO를 이용한 파일 저장

## 시작하기 전에 — v1.0에서 이미 갖춰진 것들

v1.0은 아래 내용이 이미 구현되어 있다고 가정하고 이 교재를 시작한다.

- Java 17 / Spring Boot, **JPA** 기반 데이터 접근 (`@Entity`, `@SQLDelete`, `@SQLRestriction`, `JpaRepository`, QueryDSL)
- 이메일/비밀번호 로그인, JWT(Access/Refresh Token) 발급·재발급
- `users` 테이블의 `role`(`NORMAL`/`SUPER`) 컬럼과, JWT의 `role` claim을 읽어 `SimpleGrantedAuthority("ROLE_" + role)`로 등록하는 `SecurityAuthenticationProvider`
- `@EnableMethodSecurity`가 켜져 있어 `@PreAuthorize`를 쓸 수 있는 상태
- 게시글 목록/상세 조회, 작성(SUPER 권한), 프로필·게시글 이미지 로컬 디스크 업로드

이 교재에서 새로 다루는 것은 **① 단일 서버를 서비스로 쪼개는 것(MSA), ② 게시글 삭제 기능과 그 인가 체계, ③ 로컬 디스크를 MinIO로 바꾸는 것, ④ 카카오 소셜 로그인 추가** 네 가지다.

---

## v1.0 → v2.0 핵심 변화 요약

| 구분 | v1.0 | v2.0 | 핵심 개념 |
|------|------|------|------|
| **아키텍처** | Monolithic (단일 서버) | MSA — `auth-service` + `post-service` + API Gateway | 서비스 경계 설계, API Gateway, 서비스 자율성과 데이터 소유권 |
| **인증 방식** | 이메일/비밀번호 전용 | 이메일 + 카카오 소셜 로그인 | OAuth2, Spring Security OAuth2 Client |
| **권한 관리** | SUPER만 게시글 작성 가능(삭제 없음) | SUPER 권한으로 본인 게시글 삭제까지 가능 | Spring Security 인가, `@PreAuthorize`, Gateway와 서비스 간 인증 정보 전달 |
| **파일 저장** | 로컬 디스크 저장/서빙 | MinIO 오브젝트 스토리지 (공용 버킷 + prefix로 도메인 구분) | 오브젝트 스토리지, 공용 인프라 공유, public 버킷 설계 |

---

## v2.0 최종 아키텍처

```
[Vue 3 프론트엔드]
        │
        ▼
[API Gateway]  :8080          ← Spring Cloud Gateway. JWT 검증 후 X-User-Id/X-User-Role 헤더 주입
   │                 │
   │ /api/auth/...   │ /api/posts/...
   ▼                 ▼
[auth-service] :8081     [post-service] :8082
   │      │                  │      │
   ▼      │                  ▼      │
[auth_db] └────────┬─────────[post_db]
                    ▼
      [MinIO 공용 버킷 `meerkat`]
      (prefix: /auth/profiles, /post/images)
```

- **API Gateway**: 라우팅, JWT 검증, CORS, 인증 정보를 헤더로 하위 서비스에 전달 (인가 판단은 하지 않는다)
- **auth-service**: 로그인/회원가입/토큰 재발급/유저 조회/카카오 소셜 로그인/프로필 이미지 업로드, `auth_db` 소유
- **post-service**: 게시글 CRUD/게시글 이미지 업로드, `post_db` 소유
- **MinIO**: 프로젝트 공용 버킷 1개를 auth-service/post-service가 prefix(`/auth/profiles`, `/post/images`)로 나눠 쓴다

> 두 서비스는 서로 직접 통신하지 않는다. 게시글 작성자의 닉네임·프로필처럼 다른 서비스가 가진 데이터가 필요하면, 각 서비스는 자기 데이터만 반환하고 **프론트엔드가 두 응답을 조합(클라이언트 조인)**한다.

---

## 학습 순서

| 순서 | 챕터 | 내용 |
|------|------|------|
| 1 | [MSA 전환](./01-msa-architecture.md) | 서비스 경계 정의, API Gateway 구축, auth-service/post-service 분리 |
| 2 | [권한(인가)](./02-role-permission.md) | Gateway와 서비스가 함께 만드는 인가 체계, 게시글 삭제 기능 |
| 3 | [파일 스토리지](./03-minio-file-storage.md) | 로컬 디스크 → MinIO 오브젝트 스토리지 |
| 4 | [카카오 소셜 로그인](./04-social-login-kakao.md) | OAuth2 기반 카카오 로그인 연동 |

MSA 전환을 먼저 배우는 이유: 이후 권한·파일 스토리지·소셜 로그인 모두 "이미 서비스가 나뉜 상태"를 전제로 설명하기 때문이다. 아키텍처를 먼저 이해해야 나머지 내용이 왜 그렇게 설계됐는지 자연스럽게 이해된다.
