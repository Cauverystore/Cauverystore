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

/** An HSN code: 4, 6 or 8 digits. Blank passes; use @NotBlank where the field is required. */
@Documented
@Constraint(validatedBy = ValidHsn.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidHsn {
    String message() default "must be an HSN code of 4, 6 or 8 digits";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidHsn, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext ctx) {
            return ComplianceFormats.isValidHsn(value);
        }
    }
}
