package com.micomuna.dtos;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

// Contraseña de al menos 8 caracteres y de hasta 72 bytes en UTF-8 (el límite de BCrypt).
@Documented
@Constraint(validatedBy = ClaveValidaValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface ClaveValida {

	String message() default "La contraseña no es válida";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
