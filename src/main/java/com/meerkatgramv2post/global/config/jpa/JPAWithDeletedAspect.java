package com.meerkatgramv2post.global.config.jpa;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class JPAWithDeletedAspect {
    private final EntityManager entityManager;

    // @Around: 부가 기능 처리 중간에 핵심 비지니스 로직이 실행되어야 하는 Advice
    // 어노테이션 명은 파라미터의 변수명과 일치해야 한다. (바인딩 규칙)
    @Around("@annotation(jpaWithDeleted)")
    public Object executeWithoutFiltering(ProceedingJoinPoint joinPoint, JPAWithDeleted jpaWithDeleted) throws Throwable {
        // Hibernate Session 획득
        Session session = entityManager.unwrap(Session.class);
        String filterName = jpaWithDeleted.filterName();

        // 핵심 비지니스 로직 호출 전 JPA의 필터 상태를 기록
        boolean wasEnabled = session.getEnabledFilter(filterName) != null;

        try {
            // 필터 비활성화
            session.disableFilter(filterName);

            // 원래 호출하려 했던 핵심 비지니스 로직의 메소드 호출
            return joinPoint.proceed();
        } finally {
            // 이전에 해당 필터가 활성화 되어 있을 경우에만, 해당 필터 다시 활성화
            if(wasEnabled) {
                session.enableFilter(filterName);
            }
        }
    }
}
