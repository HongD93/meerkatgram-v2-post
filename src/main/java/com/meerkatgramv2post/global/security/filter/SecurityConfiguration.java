package com.meerkatgramv2post.global.security.filter;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 메소드 레벨 권한 제어 활성화
@RequiredArgsConstructor
public class SecurityConfiguration {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity, com.meerkatgramv2post.global.security.filter.HeaderAuthenticationFilter headerAuthenticationFilter) throws Exception {
        return httpSecurity
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 비활성화
            .httpBasic(AbstractHttpConfigurer::disable) // 화면 생성 비활성화
            .formLogin(AbstractHttpConfigurer::disable) // 폼로그인 기능 비활성화
            .csrf(AbstractHttpConfigurer::disable) // CSRF 토큰 인증 비활성화
            .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(request -> request.anyRequest().permitAll()) // 인증 여부와 무관하게 모든 요청 통과
            .build();
    }
}
