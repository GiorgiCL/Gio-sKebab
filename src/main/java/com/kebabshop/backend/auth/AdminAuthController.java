package com.kebabshop.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.util.Locale;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/auth")
class AdminAuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts;

    AdminAuthController(AuthenticationManager authenticationManager, SecurityContextRepository contexts) {
        this.authenticationManager = authenticationManager;
        this.contexts = contexts;
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/login")
    AdminResponse login(@RequestBody LoginRequest login, HttpServletRequest request,
                        HttpServletResponse response) {
        if (login.email() == null || login.email().isBlank() || login.email().length() > 254
                || login.password() == null || login.password().isBlank()
                || login.password().length() > 256
                || login.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidLoginRequestException();
        }
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        login.email().trim().toLowerCase(Locale.ROOT), login.password()));
        request.getSession(true);
        request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return new AdminResponse(authentication.getName());
    }

    @GetMapping("/me")
    AdminResponse me(Authentication authentication) {
        return new AdminResponse(authentication.getName());
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    ResponseEntity<ProblemDetail> invalidCredentials() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler({InvalidLoginRequestException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalidRequest() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid login request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> unsupportedMediaType() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content type must be application/json");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problem);
    }

    private static class InvalidLoginRequestException extends RuntimeException {}

    record LoginRequest(String email, String password) {
        @Override
        public String toString() {
            return "LoginRequest[redacted]";
        }
    }
    record AdminResponse(String email) {}
    record CsrfResponse(String headerName, String token) {}
}
