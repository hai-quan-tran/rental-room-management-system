package com.rentalroom.management.service;

import com.rentalroom.management.dto.response.AuthResponse;
import com.rentalroom.management.entity.Account;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.RefreshToken;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.AccountRepository;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.RefreshTokenRepository;
import com.rentalroom.management.security.JwtProperties;
import com.rentalroom.management.security.JwtTokenProvider;
import com.rentalroom.management.security.TokenHasher;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class AuthService {

    private final AccountRepository accountRepository;
    private final BranchRepository branchRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;

    public AuthService(AccountRepository accountRepository,
                        BranchRepository branchRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider,
                        JwtProperties jwtProperties,
                        StringRedisTemplate redisTemplate) {
        this.accountRepository = accountRepository;
        this.branchRepository = branchRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.redisTemplate = redisTemplate;
    }

    public AuthResponse login(String username, String rawPassword) {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Sai tài khoản hoặc mật khẩu"));

        if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            throw new BadCredentialsException("Sai tài khoản hoặc mật khẩu");
        }
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Tài khoản đã bị khóa");
        }

        return issueTokens(account);
    }

    public AuthResponse refresh(String refreshTokenPlain) {
        if (!jwtTokenProvider.isValid(refreshTokenPlain)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Refresh token không hợp lệ hoặc đã hết hạn");
        }
        Claims claims = jwtTokenProvider.parseClaims(refreshTokenPlain);
        Long userId = Long.valueOf(claims.getSubject());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(refreshTokenPlain))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "Refresh token không hợp lệ"));

        if (stored.isRevoked()
                || stored.getExpiresAt().isBefore(LocalDateTime.now())
                || !stored.getAccount().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Refresh token không hợp lệ hoặc đã hết hạn");
        }

        Account account = stored.getAccount();
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Tài khoản đã bị khóa");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokens(account);
    }

    public void logout(String accessToken, String refreshTokenPlain) {
        if (accessToken != null && jwtTokenProvider.isValid(accessToken)) {
            Duration ttl = jwtTokenProvider.remainingValidity(accessToken);
            if (!ttl.isZero()) {
                redisTemplate.opsForValue().set(JwtTokenProvider.blacklistKey(accessToken), "1", ttl);
            }
        }
        if (refreshTokenPlain != null) {
            refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(refreshTokenPlain))
                    .ifPresent(token -> {
                        token.setRevoked(true);
                        refreshTokenRepository.save(token);
                    });
        }
    }

    private AuthResponse issueTokens(Account account) {
        List<Long> branchIds = account.getRole() == Role.ADMIN_CAP_1
                ? branchRepository.findByManagerAccountId(account.getId()).stream().map(Branch::getId).toList()
                : List.of();

        String accessToken = jwtTokenProvider.generateAccessToken(
                account.getId(), account.getUsername(), account.getRole(), branchIds);
        String refreshTokenPlain = jwtTokenProvider.generateRefreshToken(account.getId());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setAccount(account);
        refreshToken.setTokenHash(TokenHasher.sha256Hex(refreshTokenPlain));
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(jwtProperties.refreshTokenExpirationDays()));
        refreshTokenRepository.save(refreshToken);

        long expiresInSeconds = Duration.ofMinutes(jwtProperties.accessTokenExpirationMinutes()).toSeconds();
        return AuthResponse.of(accessToken, refreshTokenPlain, expiresInSeconds);
    }
}
