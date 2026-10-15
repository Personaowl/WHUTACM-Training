package com.whut.training.domain.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRegisterRequestTest {
    private UserRegisterRequest request() {
        var request = new UserRegisterRequest();
        request.setUsername("member");
        request.setDisplayName("Member");
        request.setPassword("password123");
        return request;
    }

    @Test
    void schoolFieldsMayBeOmittedOrLeftBlank() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = request();
            assertThat(validator.validate(request)).isEmpty();
            request.setRealName("");
            request.setCollege("");
            request.setClassName("");
            assertThat(validator.validate(request)).isEmpty();
        }
    }

    @Test
    void schoolFieldsHaveTheSameLengthLimitsAsProfileEditing() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = request();
            request.setRealName("x".repeat(101));
            request.setCollege("x".repeat(151));
            request.setClassName("x".repeat(151));
            assertThat(factory.getValidator().validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("realName", "college", "className");
        }
    }
}
