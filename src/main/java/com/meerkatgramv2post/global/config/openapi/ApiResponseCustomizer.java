package com.meerkatgramv2post.global.config.openapi;

import com.meerkatgramv2post.global.response.constant.CustomResponseCode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.*;

@Component // 1. 스프링이 관리하는 빈으로 등록하여, 앱 실행 시 Swagger 설정에 자동으로 개입하도록 함
public class ApiResponseCustomizer implements OperationCustomizer { // 2. Swagger 문서를 화면에 그리기 직전에 가로채서 조작할 수 있게 해주는 인터페이스

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        // 3. 현재 스캔 중인 컨트롤러 메서드(handlerMethod)에 @CustomApiResponse 어노테이션이 붙어 있는지 확인
        CustomApiResponse annotation = handlerMethod.getMethodAnnotation(CustomApiResponse.class);

        if (annotation == null) {
            // 4. 어노테이션이 안 붙어있다면 우리가 문서를 조작할 필요가 없으니, 원래 문서(operation)를 그대로 통과시킴
            return operation;
        }

        // 5. HTTP 상태 코드(예: 400, 404)를 '키'로, 그에 속하는 커스텀 에러들의 목록을 '값'으로 담을 바구니 준비
        Map<Integer, List<CustomResponseCode>> errorCodeMap = new HashMap<>();

        // 6. 어노테이션에 등록해둔 에러 코드들(annotation.value())을 하나씩 꺼내면서 반복
        for(CustomResponseCode injectErrorCode : annotation.value()) {
            int httpStatus = injectErrorCode.getHttpStatus().value(); // 해당 에러의 HTTP 상태 코드 숫자(예: 400)만 추출

            // 7. 바구니에 해당 상태 코드(400)가 없으면 빈 리스트를 만들고, 거기에 현재 에러를 쏙 넣음 (상태코드별로 묶어주는 핵심 로직)
            errorCodeMap.computeIfAbsent(httpStatus, item -> new ArrayList<>()).add(injectErrorCode);
        }

        // 8. 상태코드별로 분류가 끝난 바구니를 꺼내서, Swagger 문서 객체로 변환 시작
        errorCodeMap.forEach((httpStatus, customErrorCodeList) -> {
            Content content = new Content(); // Swagger 응답 탭의 전체 내용물을 담당할 객체
            MediaType mediaType = new MediaType(); // 어떤 데이터 형식(json, xml 등)으로 보여줄지 결정하는 객체

            // 9. 동일한 상태 코드에 속한 에러들을 순회하며, Swagger 콤보박스(Dropdown)에서 선택할 수 있는 예시(Example)를 하나씩 생성
            customErrorCodeList.forEach(customErrorCode -> {
                Map<String, Object> exampleMap = new LinkedHashMap<>();  // @ExampleObject 안쪽 내용물 만들기 (순서 보장을 위해 LinkedHashMap 사용)
                exampleMap.put("code", customErrorCode.getCode());
                exampleMap.put("message", customErrorCode.name());
                exampleMap.put("data", null);
                mediaType.addExamples(customErrorCode.name(), new Example().value(exampleMap));  // 결과물을 mediaType에 담는다 (예시 이름과 내용을 짝지어서 추가)
            });
            content.addMediaType("application/json", mediaType);  // 저장해서 content에 담는다 (우리는 JSON 형식으로 응답할 것이므로 'application/json'에 할당)

            // 10. 최종 완성된 응답 정보(에러 설명 + 에러 예시 목록)를 Swagger 문서(operation)의 해당 상태 코드 위치(예: "400")에 끼워 넣음
            operation.getResponses().addApiResponse(
                    String.valueOf(httpStatus),
                    new ApiResponse().description("에러 응답").content(content)
            );
        });

        // 11. 우리의 입맛대로 커스텀이 완료된 Swagger 문서 객체를 반환해서 화면에 그리게 함
        return operation;
    }
}
