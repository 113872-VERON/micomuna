package com.micomuna.dtos;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

// Latitud y longitud van juntas o no va ninguna. El error se marca en el campo que falta,
// para que el formulario lo muestre donde corresponde.
@Documented
@Constraint(validatedBy = CoordenadasJuntasValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CoordenadasJuntas {

	String message() default "La latitud y la longitud van juntas";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
