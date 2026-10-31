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

/** A CDSCO registration or licence number (format check only). Blank passes; use @NotBlank where the field is required. */
@Documented
@Constraint(validatedBy = ValidCdsco.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCdsco {
    String message() default "does not look like a CDSCO registration or licence number";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidCdsco, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext ctx) {
            return ComplianceFormats.isValidCdsco(value);
        }
    }
}
