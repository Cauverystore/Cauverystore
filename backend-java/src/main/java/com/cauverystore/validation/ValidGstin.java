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

/** A GSTIN: 15 characters with a correct check digit. Blank passes; use @NotBlank where the field is required. */
@Documented
@Constraint(validatedBy = ValidGstin.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidGstin {
    String message() default "is not a valid GSTIN (15 characters; the last one is a check digit)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidGstin, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext ctx) {
            return ComplianceFormats.isValidGstin(value);
        }
    }
}
