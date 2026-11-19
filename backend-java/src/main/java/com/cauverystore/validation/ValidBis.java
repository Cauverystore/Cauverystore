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

/** A BIS number: R-12345678 (CRS registration) or CM/L-1234567 (ISI licence). Blank passes; use @NotBlank where the field is required. */
@Documented
@Constraint(validatedBy = ValidBis.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBis {
    String message() default "must be a BIS registration (R-12345678) or ISI licence number (CM/L-1234567)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidBis, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext ctx) {
            return ComplianceFormats.isValidBis(value);
        }
    }
}
