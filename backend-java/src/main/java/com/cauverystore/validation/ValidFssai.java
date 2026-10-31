package com.cauverystore.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** An FSSAI licence or registration number: 14 digits. Blank passes; use @NotBlank where the field is required. */
@Documented
@Constraint(validatedBy = ValidFssai.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidFssai {
    String message() default "must be a 14-digit FSSAI licence or registration number";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidFssai, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext ctx) {
            return ComplianceFormats.isValidFssai(value);
        }
    }
}
