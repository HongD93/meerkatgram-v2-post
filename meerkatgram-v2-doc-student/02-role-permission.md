# 2. 권한(인가) — Gateway와 서비스가 함께 만드는 인가 체계

[MSA 전환](./01-msa-architecture.md)에서 만든 Gateway + post-service 구조 위에서, **게시글 삭제 기능**을 SUPER 권한으로 추가한다. 이 챕터의 핵심은 "인증 정보가 JWT에서 나왔든 헤더에서 나왔든, `@PreAuthorize`는 SecurityContext만 보고 동작한다"는 것이다.

**목표 상태:**

| 권한 | 가능한 행동 |
|------|-----------|
| `NORMAL` | 로그인만 가능. 게시글 작성·삭제는 불가 |
| `SUPER` | 게시글 작성 + 본인 게시글 삭제 |

---

## 1강. Spring Security 권한 구조 이해

### 학습 목표
- Spring Security에서 인증(Authentication)과 인가(Authorization)의 차이를 설명할 수 있다
- `GrantedAuthority`가 무엇인지 이해하고, JWT/헤더의 role 정보가 어떻게 권한으로 등록되는지 설명할 수 있다

### 1-1. 인증 vs 인가

| 용어 | 질문 | 이 프로젝트에서 담당하는 곳 |
|------|------|---------|
| **인증 (Authentication)** | 이 요청을 보낸 사람이 누구인가? (신원 확인) | Gateway의 `AuthFilter`(JWT 검증) + 각 서비스의 `HeaderAuthenticationFilter`(헤더 신뢰) |
| **인가 (Authorization)** | 이 사용자가 이 기능을 쓸 수 있는가? (권한 확인) | 각 서비스의 `@PreAuthorize` |

> 쉽게 말하면: 인가는 "출입증(토큰)이 있어도 VIP 라운지는 골드 회원만 들어갈 수 있다"는 것이다.

### 1-2. GrantedAuthority와 ROLE_ 접두사

Spring Security에서 권한은 **`GrantedAuthority`** 인터페이스로 표현한다.

```
Authentication
    │
    ├─ principal   → 유저 식별자
    ├─ credentials → null
    └─ authorities → List<GrantedAuthority>  ← 이 부분에 권한을 등록
                         │
                         └─ SimpleGrantedAuthority("ROLE_NORMAL")
                            SimpleGrantedAuthority("ROLE_SUPER")
```

> 쉽게 말하면: `GrantedAuthority`는 "이 사람이 가진 권한 배지 목록"이다.

**`hasRole("SUPER")`와 `"ROLE_SUPER"` 접두사 관계:**

Spring Security의 `hasRole()`은 내부적으로 `"ROLE_"` 접두사를 자동으로 붙여서 비교한다.

```java
// 권한 등록 시
new SimpleGrantedAuthority("ROLE_SUPER")   // "ROLE_" 접두사 필수

// 권한 확인 시
@PreAuthorize("hasRole('SUPER')")           // "ROLE_" 생략해도 내부적으로 "ROLE_SUPER"로 비교
```

> 쉽게 말하면: `hasRole('SUPER')`라고 쓰면 Spring Security가 알아서 `ROLE_SUPER`를 찾는다 — 내부 규칙이니 외우면 된다.

### 1-3. 이 권한이 어디서, 언제 등록되는가

JWT를 발급할 때부터 `role`이 담겨 있다.

```java
// JwtProvider.java (Gateway가 검증하는 대상, auth-service가 발급)
private String generateToken(User user, long ttl) {
    return Jwts.builder()
        .subject(String.valueOf(user.getId()))
        .claim("role", user.getRole())   // ← role이 JWT claim에 포함됨
        .signWith(this.secretKey)
        .compact();
}
```

그 `role`이 실제 `GrantedAuthority`로 등록되는 지점은 서비스마다 다르다 — 자세한 흐름은 2강에서 다룬다.

### 1-4. `@EnableMethodSecurity`

`@PreAuthorize`를 쓰려면 `SecurityConfiguration`에 이 어노테이션이 있어야 한다. auth-service/post-service 모두 [MSA 챕터](./01-msa-architecture.md)에서 이미 설정해뒀다.

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)   // ← @PreAuthorize 활성화
public class SecurityConfiguration {
    // ...
}
```

> 쉽게 말하면: `@EnableMethodSecurity`는 `@PreAuthorize`라는 문지기를 활성화하는 스위치다.

### 1강 정리
- 인증(누구인가)과 인가(무엇을 할 수 있는가)는 다른 개념이다
- `SimpleGrantedAuthority("ROLE_" + role)`로 권한이 등록되면 `@PreAuthorize("hasRole('...')")`가 그 값을 검사한다
- 이 권한 등록은 JWT의 `role` claim에서 출발하지만, MSA에서는 그 값이 헤더를 거쳐 전달된다(2강)

---

## 2강. MSA에서 인증 정보가 흐르는 경로 — Gateway → post-service

### 학습 목표
- Gateway(SCG)가 JWT를 검증해 헤더로 바꾸는 과정을 설명할 수 있다
- post-service가 그 헤더를 `SecurityContext`로 바꾸는 과정을 설명할 수 있다
- `@PreAuthorize`가 인증 정보의 출처(JWT vs 헤더)와 무관하게 동작하는 이유를 설명할 수 있다

인가 체계는 한 클래스가 아니라 **Gateway → post-service** 두 단계로 나뉘어 완성된다. 각 단계에서 하는 일과 하지 않는 일을 명확히 구분하는 것이 이 챕터의 핵심이다.

### 2-1. 1단계 — Gateway(`AuthFilter`)가 JWT를 헤더로 바꾼다

Gateway는 `Authorization: Bearer {JWT}` 헤더를 검증하고, 그 안의 `role`을 꺼내 `X-User-Id`/`X-User-Role` 헤더로 바꿔서 하위 서비스로 넘긴다. 구현은 [MSA 챕터 2-4](./01-msa-architecture.md)에서 이미 다뤘다.

```java
// AuthFilter.java (api-gateway) — 핵심 부분만 발췌
Optional<String> token = jwtProvider.extractAccessToken(exchange);
if (token.isEmpty()) {
    return chain.filter(exchange);   // 토큰 없으면 익명 요청으로 통과
}
Claims claims = jwtProvider.extractClaims(token.get());

ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
    .headers(httpHeaders -> httpHeaders.remove("Authorization"))
    .header("X-User-Id",   claims.getSubject())
    .header("X-User-Role", claims.get("role", String.class))
    .build();
```

**여기서 Gateway가 하지 않는 일:** "이 role이 이 요청을 허용하는가"는 판단하지 않는다. 토큰이 유효하면 헤더만 채워서 무조건 통과시킨다. 인가 판단은 전부 하위 서비스의 몫이다.

### 2-2. 2단계 — post-service(`HeaderAuthenticationFilter`)가 헤더를 SecurityContext로 바꾼다

post-service는 Gateway가 준 헤더를 신뢰하고, 그걸로 `SecurityContext`를 채운다. 구현은 [MSA 챕터 4-2](./01-msa-architecture.md)에서 이미 다뤘다.

```java
// HeaderAuthenticationFilter.java (post-service) — 핵심 부분만 발췌
String userId = request.getHeader("X-User-Id");
String role = request.getHeader("X-User-Role");

if (StringUtils.isNotBlank(userId) && StringUtils.isNotBlank(role)) {
    Authentication authentication = new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
    );
    SecurityContextHolder.getContext().setAuthentication(authentication);
}
```

이 시점에 비로소 1강에서 다룬 `GrantedAuthority`(`ROLE_SUPER` 등)가 `SecurityContext`에 등록된다.

### 2-3. 3단계 — `@PreAuthorize`는 출처를 모른다

```java
@PreAuthorize("hasRole('SUPER')")
@PostMapping("/api/posts")
public ResponseEntity<GlobalRes<PostShowRes>> store(...) { ... }
```

`@PreAuthorize`는 `SecurityContext`에 있는 `GrantedAuthority`만 검사한다. 그 값이 JWT를 직접 파싱해서 나온 것인지, Gateway가 넘겨준 헤더에서 나온 것인지는 전혀 알지 못한다 — 그래서 인증 로직(Gateway/필터)이 바뀌어도 인가 로직(`@PreAuthorize`)은 그대로 재사용된다.

> **왜 헤더 값을 `if`로 직접 비교하지 않는가?** `if (role.equals("SUPER"))` 같은 코드는 "Gateway가 헤더를 주입하는 이 프로젝트"에서만 통하는 지식이라 재사용성이 낮다. 반면 `SecurityFilterChain` + 커스텀 필터 + `@PreAuthorize`는 인증 소스가 무엇이든(JWT 직접 검증이든, 헤더 신뢰든) 거의 그대로 옮겨가는 범용 스킬이다.

> **보안 한계:** 이건 아키텍처적으로 더 "안전해지는" 게 아니다. `HeaderAuthenticationFilter`는 헤더 값을 무조건 신뢰하므로, 누군가 Gateway를 거치지 않고 post-service:8082를 직접 호출해 헤더를 위조하면 `@PreAuthorize`도 그대로 뚫린다. 진짜 보안 경계는 "post-service에 Gateway 말고는 아무도 접근할 수 없다"는 네트워크 격리(내부망, k8s NetworkPolicy 등)이며, 이 교재에서는 다루지 않는다.

### 2-4. `@PreAuthorize` 주요 표현식

| 표현식 | 의미 |
|--------|------|
| `hasRole('SUPER')` | SUPER 권한 보유 |
| `hasAnyRole('NORMAL', 'SUPER')` | NORMAL 또는 SUPER 중 하나 보유 |
| `isAuthenticated()` | 로그인 상태 |
| `#userId == authentication.principal.subject` | 메서드 파라미터와 인증 사용자 비교 |

### 2강 정리
- Gateway는 **인증만** 한다 — JWT를 검증해 헤더로 바꾸고, 통과시킨다
- post-service의 `HeaderAuthenticationFilter`는 그 헤더를 신뢰해 `SecurityContext`를 채운다
- `@PreAuthorize`는 `SecurityContext`만 보고 판단한다 — 인증 정보가 어디서 왔는지는 몰라도 된다
- 진짜 보안 경계는 네트워크 격리다. `@PreAuthorize`는 "정상 경로로 온 요청"을 전제로 한 인가 체크일 뿐이다

---

## 3강. 게시글 삭제 API 구현 (SUPER 전용, 본인 글만)

### 학습 목표
- `@PreAuthorize`로 메서드 단위 권한 제어를 구현할 수 있다
- SUPER 권한 + 본인 글 검증을 함께 만족하는 삭제 기능을 구현할 수 있다

`DELETE /api/posts/{id}`는 아직 post-service에 구현되어 있지 않다 — 이번 강의에서 신규로 구현한다.

```java
// PostController.java (post-service)
@PreAuthorize("hasRole('SUPER')")        // SUPER만 접근 가능
@DeleteMapping("/api/posts/{id}")
public ResponseEntity<GlobalRes<Void>> destroy(
    Authentication authentication,       // HeaderAuthenticationFilter가 채워준 인증 정보
    @PathVariable Long id
) {
    Long userId = Long.parseLong(authentication.getName());
    postService.destroy(id, userId);
    return ResponseEntity.ok(GlobalRes.success());
}
```

```java
// PostService.java (post-service)
@Transactional(rollbackFor = Exception.class)
public void destroy(long postId, long requestUserId) {
    Post post = postRepository.findById(postId)
        .orElseThrow(() -> new DeletedRecordException("이미 삭제된 게시글입니다."));

    // 본인 게시글인지 확인 (SUPER라도 타인 글은 삭제할 수 없다)
    if (!post.getUserId().equals(requestUserId)) {
        throw new AccessDeniedException("본인 게시글만 삭제할 수 있습니다.");
    }

    postRepository.delete(post);   // @SQLDelete가 UPDATE로 치환 → 소프트 삭제
}
```

- **NORMAL**은 `@PreAuthorize("hasRole('SUPER')")`에서 걸러지므로 삭제를 시도할 수 없다.
- **SUPER라도 타인 게시글**은 Service 레이어의 작성자 검증에서 걸러진다 — "관리자니까 아무 글이나 지울 수 있다"는 기능은 이 프로젝트에서 제공하지 않는다.
- 본인 글 검증까지 `@PreAuthorize`의 SpEL로 올리려면 커스텀 `PermissionEvaluator`가 필요하다 — 여기서는 Service 레이어의 `if` 검증으로 충분하다.

### 3강 정리

| 파일 | 변경 내용 |
|------|---------|
| `PostController.java` | `DELETE /api/posts/{id}` 신규 추가 + `@PreAuthorize("hasRole('SUPER')")` |
| `PostService.java` | `destroy()` 신규 추가 — 작성자(본인) 검증 포함 |

---

## 4강. 테스트

### 4-1. post-service 단독 테스트 (Gateway 없이 헤더 직접 주입)

```
# SUPER 유저, 본인 글 → 200 성공
DELETE http://localhost:8082/api/posts/1
Headers:
  X-User-Id: 1
  X-User-Role: SUPER

# NORMAL 유저 → 403 (@PreAuthorize에서 걸림)
DELETE http://localhost:8082/api/posts/1
Headers:
  X-User-Id: 2
  X-User-Role: NORMAL

# SUPER 유저, 타인 글 → 403 (Service 레이어 작성자 검증에서 걸림)
DELETE http://localhost:8082/api/posts/2
Headers:
  X-User-Id: 1
  X-User-Role: SUPER

# 헤더 없이(익명) → 401 — @PreAuthorize("hasRole('SUPER')")가 익명 요청도 막는지 확인하는 안전망 테스트
DELETE http://localhost:8082/api/posts/1
```

**응답 예시 (NORMAL 유저 호출 시):**
```json
{
  "code": "E03",
  "message": "UNAUTHORIZED_ERROR",
  "data": null
}
// HTTP 403 Forbidden
```

### 4-2. Gateway 경유 통합 테스트 (실제 JWT 사용)

```
1. DB에서 특정 유저의 role을 SUPER로 직접 수정
2. 해당 유저로 로그인 → SUPER 토큰 발급 확인
   POST http://localhost:8080/api/auth/login

3. Postman에서 본인 게시글 삭제 호출 → 200 응답 확인
   DELETE http://localhost:8080/api/posts/1
   Headers: Authorization: Bearer {SUPER 토큰}

4. 다른 유저의 게시글 삭제 시도 → 403 응답 확인
5. NORMAL 유저 토큰으로 동일 API 호출 → 403 응답 확인
```

Gateway가 JWT를 검증해 `X-User-Id`/`X-User-Role` 헤더로 바꾸고, post-service가 그 헤더로 `SecurityContext`를 채운 뒤 `@PreAuthorize`가 판단한다 — 4-1의 헤더 직접 테스트와 최종 결과는 같아야 한다. 다르다면 Gateway ↔ post-service 사이 어딘가(2강)가 잘못 연결된 것이다.

---

## 이 챕터에서 구현한 것

- [ ] `PostController`/`PostService`: `DELETE /api/posts/{id}` 추가 (`@PreAuthorize("hasRole('SUPER')")` + 본인 글 검증)
- [ ] post-service 단독 테스트: NORMAL/SUPER/타인 글/익명 4가지 케이스 확인
- [ ] Gateway 경유 통합 테스트: 실제 로그인 → JWT 발급 → 삭제 요청까지 전체 흐름 확인

다음 챕터에서는 로컬 디스크에 저장하던 프로필/게시글 이미지를 MinIO로 옮긴다 → [파일 스토리지 챕터](./03-minio-file-storage.md)
