package com.wellconverge.membership.adapters.rest;

import com.wellconverge.membership.application.EmailAlreadyRegisteredException;
import com.wellconverge.membership.application.MemberNotFoundException;
import com.wellconverge.membership.domain.AlreadyOnboardedException;
import com.wellconverge.membership.domain.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps Membership domain/application errors to HTTP responses (RFC 7807 ProblemDetail). */
@RestControllerAdvice
public class MembershipExceptionHandler {

    @ExceptionHandler(MemberNotFoundException.class)
    public ProblemDetail handleNotFound(MemberNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({EmailAlreadyRegisteredException.class, AlreadyOnboardedException.class})
    public ProblemDetail handleConflict(DomainException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Any other domain rule violation (invalid email, empty goals, future DOB, …) is a bad request. */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleBadRequest(DomainException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
