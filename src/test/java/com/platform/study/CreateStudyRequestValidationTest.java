package com.platform.study;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CreateStudyRequestValidationTest {
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsValidRequest() {
        assertThat(validate(new CreateStudyRequest("Banking interviews", 60, 15))).isEmpty();
    }

    @Test
    void rejectsBlankTitle() {
        assertThat(violatedProperties(new CreateStudyRequest("   ", 60, 15))).containsExactly("title");
    }

    @Test
    void sessionDurationBoundsAreInclusive() {
        assertThat(validate(new CreateStudyRequest("Edge", 15, 1))).isEmpty();
        assertThat(validate(new CreateStudyRequest("Edge", 240, 1))).isEmpty();
        assertThat(violatedProperties(new CreateStudyRequest("Short", 14, 1))).containsExactly("sessionDurationMin");
        assertThat(violatedProperties(new CreateStudyRequest("Long", 241, 1))).containsExactly("sessionDurationMin");
    }

    @Test
    void targetParticipantsMustBePositive() {
        assertThat(violatedProperties(new CreateStudyRequest("Zero", 60, 0))).containsExactly("targetN");
    }

    private Set<ConstraintViolation<CreateStudyRequest>> validate(CreateStudyRequest request) {
        return validator.validate(request);
    }

    private List<String> violatedProperties(CreateStudyRequest request) {
        return validate(request).stream().map(v -> v.getPropertyPath().toString()).sorted().toList();
    }
}
