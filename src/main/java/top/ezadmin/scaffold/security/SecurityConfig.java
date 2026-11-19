package top.ezadmin.scaffold.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import top.ezadmin.scaffold.domain.Permission;
import top.ezadmin.scaffold.service.AccountService;
import top.ezadmin.scaffold.service.AccountService.SignedInUser;

import java.io.IOException;
import java.util.function.Supplier;

@Configuration
public class SecurityConfig {
    private static final String DSL_DELETE = "/(?:p|ezadmin|topezadmin)/form/delete-.*";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain scaffoldSecurity(HttpSecurity http, AccountService accounts) throws Exception {
        var assetWriter = AuthorizationManagers.allOf(
                AuthorityAuthorizationManager.<RequestAuthorizationContext>hasAuthority(Permission.ASSET_READ.name()),
                AuthorityAuthorizationManager.<RequestAuthorizationContext>hasAuthority(Permission.ASSET_WRITE.name()));
        http.authorizeHttpRequests(access -> access
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/login", "/favicon.ico", "/static/ez-layout/**", "/scaffold/security.js",
                                "/scaffold/account.css",
                                "/webjars/**").permitAll()
                        .requestMatchers("/system/**").hasAuthority(Permission.SYSTEM_ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/native/assets.html", "/api/native/assets/**")
                            .hasAuthority(Permission.ASSET_READ.name())
                        .requestMatchers("/api/native/assets/**").access(assetWriter)
                        .requestMatchers(new RegexRequestMatcher(DSL_DELETE, "POST")).access(assetWriter)
                        .requestMatchers(new RegexRequestMatcher(DSL_DELETE, null)).denyAll()
                        .requestMatchers("/p/form/**", "/ezadmin/form/**", "/topezadmin/form/**").access(assetWriter)
                        .requestMatchers("/p/list/**", "/ezadmin/list/**", "/topezadmin/list/**")
                            .hasAuthority(Permission.ASSET_READ.name())
                        .requestMatchers("/p/**", "/ezadmin/**", "/topezadmin/**").denyAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/login").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new BrowserCsrfHandler()))
                .addFilterAfter(new RefreshAccountFilter(accounts), SecurityContextHolderFilter.class);
        return http.build();
    }

    public static boolean has(Authentication authentication, Permission permission) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission.name()));
    }

    // HTML forms use masked values. Scripts send the raw cookie value in the CSRF header.
    private static final class BrowserCsrfHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler masked = new XorCsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler raw = new CsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
            masked.handle(request, response, token);
            token.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
            return StringUtils.hasText(request.getHeader(token.getHeaderName()))
                    ? raw.resolveCsrfTokenValue(request, token) : masked.resolveCsrfTokenValue(request, token);
        }
    }

    private static final class RefreshAccountFilter extends OncePerRequestFilter {
        private final AccountService accounts;

        private RefreshAccountFilter(AccountService accounts) {
            this.accounts = accounts;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            Authentication authenticated = SecurityContextHolder.getContext().getAuthentication();
            if (authenticated != null && authenticated.getPrincipal() instanceof SignedInUser principal) {
                if (accounts.isCurrent(principal)) {
                    SecurityContext fresh = SecurityContextHolder.createEmptyContext();
                    fresh.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                            principal, null, accounts.authorities(principal.id())));
                    SecurityContextHolder.setContext(fresh);
                } else {
                    SecurityContextHolder.clearContext();
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        session.invalidate();
                    }
                }
            }
            chain.doFilter(request, response);
        }
    }
}
