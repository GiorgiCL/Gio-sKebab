package com.kebabshop.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    public static final String REMEMBER_UNTIL = "adminRememberUntil";
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts;
    private final Duration normalDuration;
    private final Duration rememberDuration;
    private final boolean secureCookie;

    AdminAuthController(AuthenticationManager authenticationManager, SecurityContextRepository contexts,
                        @Value("${server.servlet.session.timeout}") Duration normalDuration,
                        @Value("${app.admin.remember-me-duration}") Duration rememberDuration,
                        @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        this.authenticationManager = authenticationManager;
        this.contexts = contexts;
        this.normalDuration = normalDuration;
        if (rememberDuration.isNegative() || rememberDuration.isZero()
                || rememberDuration.getNano() != 0 || rememberDuration.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("app.admin.remember-me-duration must be whole seconds between 1 second and 30 days");
        }
        this.rememberDuration = rememberDuration;
        this.secureCookie = secureCookie;
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
        var session = request.getSession(true);
        request.changeSessionId();
        if (Boolean.TRUE.equals(login.rememberMe())) {
            session.setMaxInactiveInterval(Math.toIntExact(rememberDuration.getSeconds()));
            session.setAttribute(REMEMBER_UNTIL, Instant.now().plus(rememberDuration));
            String path = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
            response.setHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("JSESSIONID", session.getId())
                    .httpOnly(true).secure(secureCookie).sameSite("Lax").path(path)
                    .maxAge(rememberDuration).build().toString());
        } else {
            session.setMaxInactiveInterval(Math.toIntExact(normalDuration.getSeconds()));
            session.removeAttribute(REMEMBER_UNTIL);
        }
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

    record LoginRequest(String email, String password, Boolean rememberMe) {
        @Override
        public String toString() {
            return "LoginRequest[redacted]";
        }
    }
    record AdminResponse(String email) {}
    record CsrfResponse(String headerName, String token) {}
}
