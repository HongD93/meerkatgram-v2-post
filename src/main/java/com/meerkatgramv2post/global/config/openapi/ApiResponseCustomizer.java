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

@Component
public class ApiResponseCustomizer implements OperationCustomizer {
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        CustomApiResponse annotation = handlerMethod.getMethodAnnotation(CustomApiResponse.class);
        if (annotation == null) {
            return operation;
        }

        Map<Integer, List<CustomResponseCode>> errorCodeMap = new HashMap<>();

        for(CustomResponseCode injectErrorCode : annotation.value()) {
            int httpStatus = injectErrorCode.getHttpStatus().value();
            errorCodeMap.computeIfAbsent(httpStatus, item -> new ArrayList<>()).add(injectErrorCode);
        }

        errorCodeMap.forEach((httpStatus, customErrorCodeList) -> {
            Content content = new Content();
            MediaType mediaType = new MediaType();

            customErrorCodeList.forEach(customErrorCode -> {
                Map<String, Object> exampleMap = new LinkedHashMap<>();
                exampleMap.put("code", customErrorCode.getCode());
                exampleMap.put("message", customErrorCode.name());
                exampleMap.put("data", null);
                mediaType.addExamples(customErrorCode.name(), new Example().value(exampleMap));
            });
            content.addMediaType("application/json", mediaType);

            operation.getResponses().addApiResponse(
                String.valueOf(httpStatus),
                new ApiResponse().description("에러 응답").content(content)
            );
        });

        return operation;
    }
}