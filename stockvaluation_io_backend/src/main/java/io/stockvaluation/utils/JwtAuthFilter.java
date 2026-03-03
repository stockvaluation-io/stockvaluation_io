package io.stockvaluation.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Collections;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final Key signingKey;

    public JwtAuthFilter() {
        // You can still load this from environment variables for security
        String jwtSecret = "b6VCFoahh9HJSlAWj1GaZlYjWyFjdSYjFTAe3EOjeY7cIX7cUPMS9ooUJ+qF9yRNxpnp8ps4g4hoIfgDQjvpxQ==";
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // ✅ 1. Check for dify_test=true (query param or header)
        String difyTestParam = request.getParameter("dify_test");
        String difyTestHeader = request.getHeader("dify_test");

        if ("true".equalsIgnoreCase(difyTestParam) || "true".equalsIgnoreCase(difyTestHeader)) {
            // Treat as authenticated (bypass JWT)
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            "dify_test_user", // dummy user id
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEST"))
                    );

            authentication.setDetails("dify_test@example.com");
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
            return;
        }

        // ✅ 2. Otherwise proceed with normal JWT validation
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(signingKey)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String userId = claims.getSubject();
                String email = claims.get("email", String.class);
                String role = claims.get("role", String.class);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        );

                authentication.setDetails(email);
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}