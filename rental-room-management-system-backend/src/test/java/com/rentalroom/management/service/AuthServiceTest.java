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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private AccountRepository accountRepository;
    private BranchRepository branchRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private JwtProperties jwtProperties;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private AuthService authService;

    private Account account;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        branchRepository = mock(BranchRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        jwtProperties = new JwtProperties("test-secret", 15, 7);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        authService = new AuthService(accountRepository, branchRepository, refreshTokenRepository,
                passwordEncoder, jwtTokenProvider, jwtProperties, redisTemplate);

        account = new Account();
        account.setId(1L);
        account.setUsername("admin");
        account.setPasswordHash("hashed");
        account.setRole(Role.ADMIN_TONG);
        account.setActive(true);

        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- login ----------

    @Test
    void login_usernameNotFound_throwsBadCredentials() {
        when(accountRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> authService.login("ghost", "pass"));
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        when(accountRepository.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login("admin", "wrong"));
    }

    @Test
    void login_inactiveAccount_throwsUnauthenticated() {
        account.setActive(false);
        when(accountRepository.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login("admin", "pass"));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void login_adminTong_issuesTokensWithEmptyBranchIds() {
        when(accountRepository.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("admin"), eq(Role.ADMIN_TONG), eq(List.of())))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("refresh-token-plain");

        AuthResponse response = authService.login("admin", "pass");

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token-plain", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(15 * 60L, response.expiresInSeconds());
        verify(branchRepository, never()).findByManagerAccountId(anyLong());
    }

    @Test
    void login_adminCap1_issuesTokensWithManagedBranchIds() {
        account.setRole(Role.ADMIN_CAP_1);
        Branch branch1 = new Branch();
        branch1.setId(5L);
        Branch branch2 = new Branch();
        branch2.setId(6L);

        when(accountRepository.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(branchRepository.findByManagerAccountId(1L)).thenReturn(List.of(branch1, branch2));
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("admin"), eq(Role.ADMIN_CAP_1), eq(List.of(5L, 6L))))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("refresh-token-plain");

        AuthResponse response = assertDoesNotThrow(() -> authService.login("admin", "pass"));

        assertEquals("access-token", response.accessToken());
        verify(jwtTokenProvider).generateAccessToken(1L, "admin", Role.ADMIN_CAP_1, List.of(5L, 6L));
    }

    // ---------- refresh ----------

    @Test
    void refresh_invalidToken_throwsUnauthenticated() {
        when(jwtTokenProvider.isValid("bad-token")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh("bad-token"));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void refresh_notFoundInStore_throwsUnauthenticated() {
        String plain = "refresh-plain";
        when(jwtTokenProvider.isValid(plain)).thenReturn(true);
        Claims claims = claimsWithSubject("1");
        when(jwtTokenProvider.parseClaims(plain)).thenReturn(claims);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(plain))).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> authService.refresh(plain));
    }

    @Test
    void refresh_revokedToken_throwsUnauthenticated() {
        String plain = "refresh-plain";
        RefreshToken stored = new RefreshToken();
        stored.setAccount(account);
        stored.setRevoked(true);
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(jwtTokenProvider.isValid(plain)).thenReturn(true);
        Claims claims = claimsWithSubject("1");
        when(jwtTokenProvider.parseClaims(plain)).thenReturn(claims);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(plain))).thenReturn(Optional.of(stored));

        assertThrows(BusinessException.class, () -> authService.refresh(plain));
    }

    @Test
    void refresh_expiredToken_throwsUnauthenticated() {
        String plain = "refresh-plain";
        RefreshToken stored = new RefreshToken();
        stored.setAccount(account);
        stored.setRevoked(false);
        stored.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(jwtTokenProvider.isValid(plain)).thenReturn(true);
        Claims claims = claimsWithSubject("1");
        when(jwtTokenProvider.parseClaims(plain)).thenReturn(claims);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(plain))).thenReturn(Optional.of(stored));

        assertThrows(BusinessException.class, () -> authService.refresh(plain));
    }

    @Test
    void refresh_userIdMismatch_throwsUnauthenticated() {
        String plain = "refresh-plain";
        RefreshToken stored = new RefreshToken();
        stored.setAccount(account);
        stored.setRevoked(false);
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(jwtTokenProvider.isValid(plain)).thenReturn(true);
        Claims claims = claimsWithSubject("999");
        when(jwtTokenProvider.parseClaims(plain)).thenReturn(claims);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(plain))).thenReturn(Optional.of(stored));

        assertThrows(BusinessException.class, () -> authService.refresh(plain));
    }

    @Test
    void refresh_inactiveAccount_throwsUnauthenticated() {
        account.setActive(false);
        String plain = "refresh-plain";
        RefreshToken stored = new RefreshToken();
        stored.setAccount(account);
        stored.setRevoked(false);
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(jwtTokenProvider.isValid(plain)).thenReturn(true);
        Claims claims = claimsWithSubject("1");
        when(jwtTokenProvider.parseClaims(plain)).thenReturn(claims);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(plain))).thenReturn(Optional.of(stored));

        assertThrows(BusinessException.class, () -> authService.refresh(plain));
    }

    @Test
    void refresh_valid_revokesOldTokenAndIssuesNewOnes() {
        String plain = "refresh-plain";
        RefreshToken stored = new RefreshToken();
        stored.setId(10L);
        stored.setAccount(account);
        stored.setRevoked(false);
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(jwtTokenProvider.isValid(plain)).thenReturn(true);
        Claims claims = claimsWithSubject("1");
        when(jwtTokenProvider.parseClaims(plain)).thenReturn(claims);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(plain))).thenReturn(Optional.of(stored));
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("admin"), eq(Role.ADMIN_TONG), eq(List.of())))
                .thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("new-refresh-plain");

        AuthResponse response = assertDoesNotThrow(() -> authService.refresh(plain));

        assertEquals("new-access", response.accessToken());
        assertEquals("new-refresh-plain", response.refreshToken());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        assertEquals(true, captor.getAllValues().get(0).isRevoked());
    }

    // ---------- logout ----------

    @Test
    void logout_validAccessTokenWithRemainingTtl_blacklistsInRedis() {
        when(jwtTokenProvider.isValid("access")).thenReturn(true);
        when(jwtTokenProvider.remainingValidity("access")).thenReturn(Duration.ofMinutes(5));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        authService.logout("access", null);

        verify(valueOperations).set(eq(JwtTokenProvider.blacklistKey("access")), eq("1"), eq(Duration.ofMinutes(5)));
    }

    @Test
    void logout_accessTokenWithZeroTtl_doesNotBlacklist() {
        when(jwtTokenProvider.isValid("access")).thenReturn(true);
        when(jwtTokenProvider.remainingValidity("access")).thenReturn(Duration.ZERO);

        authService.logout("access", null);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void logout_invalidAccessToken_doesNotBlacklist() {
        when(jwtTokenProvider.isValid("access")).thenReturn(false);

        authService.logout("access", null);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void logout_withStoredRefreshToken_revokesIt() {
        String plain = "refresh-plain";
        RefreshToken stored = new RefreshToken();
        stored.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(plain))).thenReturn(Optional.of(stored));

        authService.logout(null, plain);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertEquals(true, captor.getValue().isRevoked());
    }

    @Test
    void logout_refreshTokenNotFound_savesNothing() {
        String plain = "unknown-plain";
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(plain))).thenReturn(Optional.empty());

        authService.logout(null, plain);

        verify(refreshTokenRepository, never()).save(any());
    }

    private Claims claimsWithSubject(String subject) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(subject);
        return claims;
    }
}
