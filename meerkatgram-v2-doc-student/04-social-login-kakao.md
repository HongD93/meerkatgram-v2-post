# 4. 카카오 소셜 로그인

이메일/비밀번호 로그인에 카카오 소셜 로그인을 추가한다. 이 기능은 [MSA 전환](./01-msa-architecture.md)에서 만든 api-gateway 뒤의 **auth-service 안에서 동작**한다. Spring Security OAuth2 Client를 사용하며, 로그인 방식이 달라도 **최종적으로 동일한 JWT를 발급**하는 것이 핵심이다.

**목표 상태:**

```
기존: 이메일 + 비밀번호 → JWT 발급
추가: 카카오 계정 → JWT 발급
```

두 방식 모두 로그인 후에는 같은 JWT를 사용하므로, 프론트엔드 나머지 로직은 변경이 없다.

---

## 1강. OAuth2 개념 및 카카오 개발자 설정

### 학습 목표
- OAuth2가 무엇인지, 왜 필요한지 설명할 수 있다
- Authorization Code Flow의 전체 흐름을 단계별로 설명할 수 있다
- 카카오 개발자 콘솔에서 앱을 등록하고 필요한 키를 발급받을 수 있다

### 1-1. 소셜 로그인이 없으면?

서비스마다 회원가입을 따로 해야 한다.

```
사용자:
  - 네이버에 가입
  - 카카오에 가입
  - 쿠팡에 가입
  - Meerkatgram에 가입  ← 또 가입...
```

> 쉽게 말하면: 우리 서비스가 직접 비밀번호를 받는 게 아니라 카카오한테 "이 사람 맞아요?"를 물어보는 방식이다. 사용자는 새 비밀번호를 안 만들어도 되고, 서비스는 비밀번호 저장/관리 책임에서 해방된다.

### 1-2. OAuth2란?

**OAuth2(Open Authorization 2.0)** 는 제3자 서비스(Meerkatgram)가 사용자의 동의 하에 카카오 계정 정보를 안전하게 가져올 수 있도록 하는 **표준 인증 위임 프로토콜**이다.

| 역할 | 이름 | Meerkatgram에서 |
|------|------|----------------|
| Resource Owner | 자원 소유자 | 로그인하는 사용자 |
| Client | 클라이언트 앱 | Meerkatgram auth-service |
| Authorization Server | 인증 서버 | 카카오 로그인 서버 |
| Resource Server | 자원 서버 | 카카오 유저 정보 API |

### 1-3. Authorization Code Flow (전체 흐름)

```
[사용자 브라우저]          [api-gateway]      [auth-service]        [카카오]
      │                      │                    │                  │
      │  ① 카카오 로그인 버튼 클릭│                    │                  │
      │──────────────────────►│───────────────────►│                  │
      │  카카오 로그인 URL 반환                        │                  │
      │◄───────────────────────────────────────────│                  │
      │                                             │                  │
      │  ② 카카오 로그인 페이지 이동 (브라우저 → 카카오 직접)                │
      │──────────────────────────────────────────────────────────────►│
      │  로그인 성공 + 동의 화면 확인                                     │
      │◄──────────────────────────────────────────────────────────────│
      │  Authorization Code 포함된 Redirect URL                        │
      │                                             │                  │
      │  ③ Redirect URL로 자동 이동 (Gateway 경유)     │                  │
      │──────────────────────►│───────────────────►│                  │
      │                                             │  ④ Code → Access Token 교환
      │                                             │─────────────────►│
      │                                             │◄─────────────────│
      │                                             │  ⑤ 카카오 유저 정보 요청
      │                                             │─────────────────►│
      │                                             │◄─────────────────│
      │                                             │  ⑥ DB 조회 or 자동 가입
      │                                             │  ⑦ Meerkatgram JWT 발급
      │◄────────────────────────────────────────────│                  │
      │  Refresh Token(쿠키) + Redirect to 콜백 페이지                    │
```

> ①~③은 브라우저와 카카오 사이에서 (Gateway를 그대로 통과하며) 자동으로 일어난다. auth-service가 실제로 처리하는 것은 ④~⑦이다. Gateway는 `/api/auth/**` 요청을 auth-service로 그대로 넘겨주는 역할만 한다 — 인증 로직에는 관여하지 않는다.

### 1-4. 카카오 개발자 설정

1. 카카오 개발자 사이트(https://developers.kakao.com)에서 앱 등록 → **REST API 키** 확보(Client ID로 사용)
2. [플랫폼] → Web 플랫폼 등록: 사이트 도메인 `http://localhost:8080`(api-gateway 주소)
3. [카카오 로그인] 활성화 ON → Redirect URI 등록: `http://localhost:8080/api/auth/oauth2/callback/kakao`
4. [동의항목] → 닉네임/프로필 사진/카카오계정(이메일) 모두 **필수 동의**로 설정
5. 필요 시 Client Secret 발급

> Spring Security OAuth2 Client의 기본 Redirect URI 패턴은 `{baseUrl}/login/oauth2/code/{registrationId}`이지만, 이 프로젝트는 `/api/auth/oauth2/callback/{registrationId}`로 옮겨 등록한다 — 이유는 2-8 참고.
>
> **이메일을 왜 필수 동의로 하는가?** `User.email`은 DB에서 `NOT NULL` 컬럼이라, 동의를 못 받아 `null`이 들어오면 유저 저장이 실패한다. 선택 동의로 두면 이 실패 케이스를 코드에서 처리해야 하는데, 애초에 필수 동의로 막아두면 그 문제 자체가 생기지 않는다.

### 1-5. 의존성 추가

```groovy
// auth-service/build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
}
```

### 1강 정리
- OAuth2 = 제3자가 대신 인증해주는 표준 프로토콜
- Authorization Code Flow: 브라우저↔카카오 인증 → Code → auth-service가 Token 교환 (Gateway는 그대로 통과시키는 역할만)
- 카카오 개발자 콘솔에서: REST API 키, Redirect URI, **이메일 필수 동의** 설정
- `spring-boot-starter-oauth2-client` 의존성을 auth-service에 추가

---

## 2강. Spring Security OAuth2 서버 측 구현

### 학습 목표
- `application.yaml`에 카카오 OAuth2 설정을 추가할 수 있다
- `OAuth2UserService`를 구현해 카카오 유저 정보를 DB와 연동할 수 있다
- 소프트 삭제된 유저의 재로그인을 안전하게 복구 처리할 수 있다
- 소셜 로그인 성공/실패 후 각각 알맞은 곳으로 리다이렉트하는 핸들러를 구현할 수 있다
- provider별 `OAuth2UserService`를 묶어주는 delegating 패턴을 구현할 수 있다

### 2-1. application.yaml OAuth2 설정

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET}
            redirect-uri: "{baseUrl}/api/auth/oauth2/callback/{registrationId}"
            authorization-grant-type: authorization_code
            client-authentication-method: client_secret_post
            scope:
              - profile_nickname
              - account_email
              - profile_image
        provider:
          kakao:
            authorization-uri: https://kauth.kakao.com/oauth/authorize
            token-uri: https://kauth.kakao.com/oauth/token
            user-info-uri: https://kapi.kakao.com/v2/user/me
            user-name-attribute: id
```

> `redirect-uri`의 `{baseUrl}`은 auth-service 자기 주소(`http://localhost:8081`)로 치환된다. 하지만 실제 브라우저는 카카오에서 Gateway 주소(`http://localhost:8080`)로 리다이렉트되어 온다 — Gateway가 `Host` 헤더를 그대로 전달하는 기본 프록시 동작 덕분에 `{baseUrl}`이 Gateway 기준으로 맞아떨어진다.

### 2-2. 카카오 응답 구조

카카오 유저 정보 API(`/v2/user/me`) 응답:

```json
{
  "id": 1234567890,
  "kakao_account": {
    "email": "user@kakao.com",
    "profile": {
      "nickname": "홍길동",
      "profile_image_url": "http://k.kakaocdn.net/..."
    }
  }
}
```

### 2-3. `KakaoOAuth2Service` 구현

```java
// auth-service/domain/auth/service/KakaoOAuth2Service.java
@Service
@RequiredArgsConstructor
public class KakaoOAuth2Service implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;

    @JPAWithDeleted   // 탈퇴(soft delete)된 유저도 조회 대상에 포함시킨다 — 2-4 참고
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = new DefaultOAuth2UserService().loadUser(userRequest);

        Map<String, Object> attributes = oAuth2User.getAttributes();
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        String email = (String) kakaoAccount.get("email");
        String nickname = (String) profile.get("nickname");
        String profileImageUrl = (String) profile.get("profile_image_url");

        User user = authRepository.findByEmail(email)
            .orElseGet(() -> createKakaoUser(email, nickname, profileImageUrl));

        // 기존 유저가 있는데 provider가 다른 경우 (이메일 로그인으로 가입한 유저)
        if (!ProviderPolicy.KAKAO.equals(user.getProvider())) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                CustomResponseCode.ALREADY_REGISTERED_ERROR.getCode(),
                CustomResponseCode.ALREADY_REGISTERED_ERROR.name(),
                null
            ));
        }

        // 탈퇴(soft delete)했던 카카오 유저가 재로그인하면 복구
        if (user.getDeletedAt() != null) {
            user.setDeletedAt(null);
            authRepository.save(user);
        }

        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())),
            Map.of("id", user.getId(), "email", user.getEmail(), "role", user.getRole()),
            "id"
        );
    }

    private User createKakaoUser(String email, String nickname, String profileImageUrl) {
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setNick(nickname);
        newUser.setProfile(profileImageUrl != null ? profileImageUrl : "");
        newUser.setProvider(ProviderPolicy.KAKAO);
        newUser.setRole(RolePolicy.NORMAL);
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        return authRepository.save(newUser);
    }
}
```

> **탈퇴 계정 + provider 다른 경우도 차단한다.** 예전에 이메일/비밀번호로 가입했다가 탈퇴한 계정이 있는 상태에서 같은 이메일로 카카오 로그인을 시도해도 "이미 가입된 계정입니다" 에러가 난다 — 탈퇴 여부와 무관하게 provider 충돌은 항상 막는 게 이 프로젝트의 정책이다. 계정 연동 기능은 범위 밖이다.
>
> **카카오 프로필 최신화는 최초 가입 시점에만 이뤄진다.** 재로그인 시 카카오 쪽 닉네임/프로필사진이 바뀌어도 DB에는 반영되지 않는다.

### 2-4. 소프트 삭제 필터와 탈퇴 유저 복구가 동작하는 원리

`User` 엔티티는 `@SQLRestriction` 대신 Hibernate `@Filter`로 소프트 삭제를 구현하는데, `@Filter`는 명시적으로 켜주지 않으면 아무 효과가 없다. 그래서 두 컴포넌트가 짝을 이룬다.

```java
// global/config/jpa/SoftDeleteFilterAspect.java
// 모든 @RestController 진입 시 필터를 기본으로 켠다 (= 탈퇴 유저는 원칙적으로 조회 제외)
@Aspect
@Component
@RequiredArgsConstructor
public class SoftDeleteFilterAspect {
    private final EntityManager entityManager;

    @Before("within(@org.springframework.web.bind.annotation.RestController *)")
    public void enableSoftDeleteFilter() {
        entityManager.unwrap(Session.class).enableFilter("softDelete");
    }
}
```

```java
// global/config/jpa/JPAWithDeletedAspect.java
// @JPAWithDeleted가 붙은 메서드 실행 중에만 필터를 잠깐 끈다 (= 탈퇴 유저도 조회)
@Aspect
@Component
@RequiredArgsConstructor
public class JPAWithDeletedAspect {
    private final EntityManager entityManager;

    @Around("@annotation(jpaWithDeleted)")
    public Object executeWithoutFiltering(ProceedingJoinPoint joinPoint, JPAWithDeleted jpaWithDeleted) throws Throwable {
        Session session = entityManager.unwrap(Session.class);
        String filterName = jpaWithDeleted.filterName();
        boolean wasEnabled = session.getEnabledFilter(filterName) != null;
        try {
            session.disableFilter(filterName);
            return joinPoint.proceed();
        } finally {
            if (wasEnabled) session.enableFilter(filterName);
        }
    }
}
```

평소(이메일 로그인 등)엔 `SoftDeleteFilterAspect`가 필터를 켜둬서 탈퇴 유저가 조회에서 제외되고, `KakaoOAuth2Service.loadUser()`(`@JPAWithDeleted`) 실행 중에만 `JPAWithDeletedAspect`가 필터를 잠깐 꺼서 탈퇴 유저도 찾아지게 한다 — 그래서 2-3의 복구 로직이 동작할 수 있다. `SoftDeleteFilterAspect`가 없으면 `@JPAWithDeleted`는 "이미 꺼져있는 필터를 또 끄는" 무의미한 코드가 되므로, 두 클래스는 항상 세트로 존재해야 한다.

### 2-5. OAuth2UserService Delegating 패턴

콜백 URL이 처리되면 Spring Security는 `registrationId`가 담긴 `OAuth2UserRequest`를 **딱 하나의 `OAuth2UserService` 빈**에 넘긴다. provider가 카카오 하나뿐이면 `KakaoOAuth2Service`를 바로 등록해도 되지만, 이후 구글/네이버 등을 추가하면 provider별 서비스가 여러 개 생기므로 위임 계층이 필요하다.

```java
// auth-service/global/security/oauth2/DelegatingOAuth2UserService.java
@Service
@RequiredArgsConstructor
public class DelegatingOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final KakaoOAuth2Service kakaoOAuth2Service;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        return switch (registrationId) {
            case "kakao" -> kakaoOAuth2Service.loadUser(userRequest);
            default -> throw new OAuth2AuthenticationException(new OAuth2Error(
                CustomResponseCode.UNSUPPORTED_PROVIDER_ERROR.getCode(),
                CustomResponseCode.UNSUPPORTED_PROVIDER_ERROR.name() + ": " + registrationId,
                null
            ));
        };
    }
}
```

> `ProviderPolicy` enum에는 `KAKAO` 외에 `GOOGLE`도 이미 정의돼 있다 — 아직 `GoogleOAuth2Service`는 없고, 향후 확장을 위해 값만 잡아둔 것이다.

### 2-6. OAuth2 로그인 성공/실패 핸들러

**성공 시**: JWT를 발급하고, refreshToken은 쿠키에만 담아 콜백 페이지로 리다이렉트한다.

```java
// auth-service/global/security/oauth2/OAuth2SuccessHandler.java
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final AuthRepository authRepository;
    private final JwtProvider jwtProvider;
    private final CookieManager cookieManager;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        DefaultOAuth2User oAuth2User = (DefaultOAuth2User) authentication.getPrincipal();
        Long userId = (Long) oAuth2User.getAttributes().get("id");

        User user = authRepository.findById(userId).orElseThrow();

        String accessToken = jwtProvider.generateAccessToken(user);
        String refreshToken = jwtProvider.generateRefreshToken(user);

        user.setRefreshToken(refreshToken);
        authRepository.save(user);
        cookieManager.setRefreshTokenToCookie(response, refreshToken);

        // accessToken은 URL에 담지 않고, 콜백 페이지로만 리다이렉트
        this.getRedirectStrategy().sendRedirect(request, response, "http://localhost:5173/oauth2/callback");
    }
}
```

> **왜 accessToken을 URL에 담지 않는가?** URL 쿼리 파라미터는 브라우저 히스토리, 서버 접근 로그, `Referer` 헤더에 그대로 남는다. 대신 프론트는 콜백 페이지에서 기존 `/api/auth/reissue-token` API(쿠키의 refreshToken을 읽어 accessToken을 재발급)를 그대로 호출한다 — 이메일 로그인과 흐름이 사실상 통일된다.

**실패 시**: 에러코드/메시지를 콜백 URL에 담아 리다이렉트한다.

```java
// auth-service/global/security/oauth2/OAuth2FailureHandler.java
@Component
@RequiredArgsConstructor
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {
    private static final Pattern OAUTH2_ERROR_CODE_PATTERN = Pattern.compile("^E3\\d$");

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        String errorCode = CustomResponseCode.OAUTH2_ERROR.getCode();
        String message = CustomResponseCode.OAUTH2_ERROR.name();

        if (exception instanceof OAuth2AuthenticationException oAuth2AuthenticationException) {
            String code = oAuth2AuthenticationException.getError().getErrorCode();
            if (OAUTH2_ERROR_CODE_PATTERN.matcher(code).matches()) {
                errorCode = code;
                message = oAuth2AuthenticationException.getError().getDescription();
            }
        }

        String redirectUri = UriComponentsBuilder.fromUri(URI.create("http://localhost:5173/oauth2/callback"))
            .queryParam("code", errorCode)
            .queryParam("message", message)
            .build()
            .toUriString();

        this.getRedirectStrategy().sendRedirect(request, response, redirectUri);
    }
}
```

> 성공 핸들러와 실패 핸들러가 **같은 콜백 경로**(`/oauth2/callback`)로 리다이렉트한다 — 성공 시엔 쿼리 파라미터가 없고, 실패 시엔 `code`/`message`가 붙는다. 프론트는 이 유무로 성공/실패를 구분한다(3-2 참고). `URLEncoder`를 직접 쓰지 않고 `UriComponentsBuilder`를 쓰면 쿼리 파라미터 인코딩을 Spring이 대신 처리해준다.

### 2-7. SecurityConfiguration — OAuth2 로그인 경로를 `/api/auth` 하위로

```java
.oauth2Login(oauth2 -> oauth2
    .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/auth/oauth2/authorization"))
    .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/auth/oauth2/callback/*"))
    .userInfoEndpoint(userInfo -> userInfo.userService(delegatingOAuth2UserService))
    .successHandler(oAuth2SuccessHandler)
    .failureHandler(oAuth2FailureHandler)
)
```

> Spring Security OAuth2 Client의 기본 경로(`/oauth2/authorization/*`, `/login/oauth2/code/*`)는 `/api/auth/**` 밖에 있다. api-gateway의 라우팅 predicate는 `/api/auth/**`, `/api/posts/**` 두 개뿐이므로([MSA 챕터](./01-msa-architecture.md)), 기본 경로 그대로 두면 Gateway가 이 요청을 auth-service로 넘겨줄 방법이 없다. 그래서 OAuth2 로그인 경로 자체를 `/api/auth` 하위로 옮겨 기존 라우트가 그대로 커버하게 만든다 — 새 엔드포인트가 생겨도 Gateway 설정을 다시 만지지 않는다는 원칙을 여기서도 지킨다.

### 2강 정리

| 파일 | 역할 |
|------|------|
| `KakaoOAuth2Service` | 카카오 유저 정보 파싱, DB 유저 생성/조회, 탈퇴 유저 복구 |
| `SoftDeleteFilterAspect`/`JPAWithDeletedAspect` | 소프트 삭제 필터 기본 활성화 + 필요할 때만 일시 해제 |
| `DelegatingOAuth2UserService` | `registrationId`에 따라 provider별 위임 |
| `OAuth2SuccessHandler`/`OAuth2FailureHandler` | JWT 발급/쿠키 설정 또는 에러코드 조립 후 콜백 페이지로 리다이렉트 |
| `SecurityConfiguration` | `oauth2Login()` 설정, 경로를 `/api/auth` 하위로 커스터마이즈 |

`provider` 필드: `NONE`(이메일 가입)/`KAKAO`(카카오 가입)/`GOOGLE`(미구현, 확장용). 같은 이메일로 provider가 충돌하면 탈퇴 여부와 무관하게 항상 차단한다.

---

## 3강. 프론트엔드 소셜 로그인 연동

### 학습 목표
- Vue 로그인 페이지에 카카오 로그인 버튼을 추가할 수 있다
- 성공/실패 각각의 경우에 맞게 콜백 페이지에서 처리할 수 있다

### 3-1. 카카오 로그인 버튼

```vue
<script setup>
const socialLogin = (provider) => {
  // 상대경로로 이동 — vite proxy(/api → gateway)를 통해 개발/배포 모두 동일 코드로 동작
  window.location.href = `/api/auth/oauth2/authorization/${provider}`;
}
</script>

<template>
  <MyButton :btn-type="'button'" :bg-image="'kakao'" @click="socialLogin('kakao')"></MyButton>
</template>
```

> `window.location.href`를 쓰는 이유: 소셜 로그인은 여러 번의 브라우저 리다이렉트가 발생하는 흐름이라 Axios가 아닌 브라우저 이동 방식을 쓴다. 절대주소가 아니라 상대경로를 쓰는 이유: 개발 서버 프록시와 배포 환경 모두에서 코드 수정 없이 동작하게 하기 위해서다.

### 3-2. OAuth2 콜백 처리 페이지

```vue
<!-- pages/auth/Oauth2Callback.vue -->
<script setup>
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../../store/auth/useAuthStore';
import { onBeforeMount } from 'vue';
import { useMyErrorStore } from '../../store/error/useMyErrorStore';

const authStore = useAuthStore();
const myErrorStore = useMyErrorStore();
const route = useRoute();
const router = useRouter();

onBeforeMount(async () => {
  try {
    const { code, message } = route.query;

    if (!code) {
      // 성공: 쿠키의 refreshToken으로 accessToken 재발급 후 홈으로
      await authStore.reissue();
      return router.replace('/');
    } else if (code == 'E31') {
      alert('다른 방식으로 이미 가입된 회원입니다.');
      return router.replace('/login');
    } else {
      throw myErrorStore.createErrorWithCodeAndMessage(code, message);
    }
  } catch (error) {
    myErrorStore.setErrorInfo(error);
    return router.replace('/error');
  }
});
</script>
```

> accessToken을 URL에서 읽지 않는다 — `OAuth2SuccessHandler`가 애초에 accessToken을 URL에 담지 않기 때문이다(2-6). 성공 시 `code` 쿼리 파라미터가 없다는 것만 확인하고, 나머지는 기존 `authStore.reissue()`를 그대로 재사용한다.

### 3-3. Vue Router 콜백 경로

```javascript
const routes = [
  { path: '/oauth2/callback', component: () => import('@/pages/auth/Oauth2Callback.vue'), meta: setMeta(false, false) },
  { path: '/login', component: () => import('@/pages/auth/Login.vue'), meta: setMeta(false, true) },
]
```

### 3-4. 전체 흐름 확인

```
[사용자] 카카오 로그인 버튼 클릭
  → window.location.href = '/api/auth/oauth2/authorization/kakao'
  → (Gateway가 auth-service로 넘기고, Spring Security가 카카오 인증 URL로 리다이렉트)
[카카오 로그인 페이지] 로그인 + 동의
  → [api-gateway] → [auth-service] /api/auth/oauth2/callback/kakao?code=...
     DelegatingOAuth2UserService → KakaoOAuth2Service: 유저 정보 파싱, DB 저장/조회, 탈퇴 유저 복구
  ├─ 성공 → OAuth2SuccessHandler: JWT 발급, refreshToken 쿠키 설정 → /oauth2/callback(파라미터 없음)
  │         → authStore.reissue() 호출 → accessToken 확보 → 메인 페이지
  └─ 실패 → OAuth2FailureHandler → /oauth2/callback?code=E3x&message=...
            → E31이면 alert 후 로그인 페이지, 그 외엔 공통 에러 페이지
```

### 3-5. 이메일 로그인 vs 카카오 로그인 비교

| 항목 | 이메일 로그인 | 카카오 로그인 |
|------|-------------|-------------|
| 요청 방식 | Axios POST | `window.location.href` |
| accessToken 전달 | JSON 응답 body | 없음 — 콜백 페이지에서 `reissue-token` API로 별도 획득 |
| refreshToken 전달 | httpOnly 쿠키 | httpOnly 쿠키 (동일) |
| 처리 페이지 | 없음 (현재 페이지) | `/oauth2/callback` (별도 페이지) |
| 실패 처리 | Axios 에러 응답 | 콜백 URL의 `code`/`message` 쿼리 파라미터 |

### 3강 정리

| 파일 | 변경 내용 |
|------|---------|
| `Login.vue` | 카카오 로그인 버튼 추가 (상대경로 URL) |
| `Oauth2Callback.vue` | 신규 — 성공/실패 분기 처리 |
| `router.js` | `/oauth2/callback` 라우트 추가 |

---

## 이 챕터에서 구현한 것

- [x] `build.gradle`: OAuth2 의존성 추가
- [x] `application.yaml`: 카카오 OAuth2 설정
- [x] `KakaoOAuth2Service` 구현 (탈퇴 유저 복구 포함)
- [x] `SoftDeleteFilterAspect` 구현 (소프트 삭제 필터 기본 활성화)
- [x] `DelegatingOAuth2UserService` 구현 (provider별 위임)
- [x] `OAuth2SuccessHandler`/`OAuth2FailureHandler` 구현
- [x] `SecurityConfiguration`: `oauth2Login()` 추가
- [x] `Login.vue`: 카카오 버튼 추가, `Oauth2Callback.vue` 생성, `router.js`에 콜백 라우트 추가
- [ ] 통합 테스트: 신규/기존/탈퇴 카카오 유저, 이메일-카카오 계정 충돌, 로그인 후 API 호출까지 확인

이것으로 v2.0 마이그레이션의 네 챕터(MSA 전환 → 권한 → 파일 스토리지 → 카카오 로그인)를 모두 마쳤다.
