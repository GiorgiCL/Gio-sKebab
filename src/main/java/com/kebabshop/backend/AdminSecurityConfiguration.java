package com.kebabshop.backend;

import com.kebabshop.backend.auth.AdminAccountRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.net.URI;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration(proxyBeanMethods = false)
class AdminSecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService adminDetails(AdminAccountRepository accounts) {
        return username -> accounts.findByEmail(username)
                .map(account -> User.withUsername(account.getEmail())
                        .password(account.getPasswordHash())
                        .disabled(!account.isEnabled())
                        .authorities("ROLE_ADMIN").build())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService adminDetails, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(adminDetails);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
                                            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
                                            AdminAccountRepository accounts) throws Exception {
        return http
                .cors(config -> config.configurationSource(cors))
                .securityContext(config -> config.securityContextRepository(contexts))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/public/restaurant",
                                "/api/public/opening-hours", "/api/public/opening-status",
                                "/api/public/menu").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/login").permitAll()
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> apiError(response, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> apiError(response, HttpStatus.FORBIDDEN)))
                .requestCache(cache -> cache.disable())
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                    FilterChain chain) throws ServletException, IOException {
                        String path = request.getRequestURI().substring(request.getContextPath().length());
                        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                        if (path.startsWith("/api/admin/") && !path.equals("/api/admin/auth/csrf")
                                && !path.equals("/api/admin/auth/login") && !path.equals("/api/admin/auth/logout")
                                && authentication != null && authentication.isAuthenticated()
                                && !(authentication instanceof AnonymousAuthenticationToken)
                                && accounts.findByEmail(authentication.getName())
                                        .filter(account -> account.isEnabled()).isEmpty()) {
                            if (request.getSession(false) != null) {
                                request.getSession(false).invalidate();
                            }
                            SecurityContextHolder.clearContext();
                        }
                        chain.doFilter(request, response);
                    }
                }, AuthorizationFilter.class)
                .logout(logout -> logout.logoutUrl("/api/admin/auth/logout")
                        .addLogoutHandler(new CookieClearingLogoutHandler("JSESSIONID"))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .build();
    }

    private static void apiError(HttpServletResponse response, HttpStatus status) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"" + status.getReasonPhrase() + "\",\"status\":" + status.value() + "}");
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${security.cors.allowed-origins:}") String origins,
                                                    Environment environment) {
        var source = new UrlBasedCorsConfigurationSource();
        if (origins.isBlank()) {
            return source; // Same-origin only until an explicit frontend origin is configured.
        }
        List<String> allowed = Arrays.stream(origins.split(",", -1)).map(String::trim).toList();
        for (String origin : allowed) {
            URI uri;
            try {
                uri = URI.create(origin);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid security.cors.allowed-origins", exception);
            }
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || environment.acceptsProfiles(Profiles.of("prod")) && !"https".equals(uri.getScheme())
                    || uri.getHost() == null || uri.getPath() != null && !uri.getPath().isEmpty()
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Invalid security.cors.allowed-origins");
            }
        }
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowed);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
