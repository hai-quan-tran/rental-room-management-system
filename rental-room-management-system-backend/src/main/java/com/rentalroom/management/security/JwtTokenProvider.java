package com.rentalroom.management.security;

import com.rentalroom.management.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_BRANCH_IDS = "branchIds";
    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String username, Role role, List<Long> branchIds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_BRANCH_IDS, branchIds)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(properties.accessTokenExpirationMinutes(), ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(properties.refreshTokenExpirationDays(), ChronoUnit.DAYS)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Time remaining until the token's own {@code exp} claim — used as the blacklist entry's TTL. */
    public Duration remainingValidity(String token) {
        Instant expiry = parseClaims(token).getExpiration().toInstant();
        Duration remaining = Duration.between(Instant.now(), expiry);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public static String blacklistKey(String token) {
        return BLACKLIST_KEY_PREFIX + TokenHasher.sha256Hex(token);
    }

    @SuppressWarnings("unchecked")
    public UserPrincipal toUserPrincipal(Claims claims) {
        List<Long> branchIds = ((List<Number>) claims.getOrDefault(CLAIM_BRANCH_IDS, List.of()))
                .stream()
                .map(Number::longValue)
                .toList();
        return new UserPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                branchIds
        );
    }
}
