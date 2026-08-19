package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.AccountCreateRequest;
import com.rentalroom.management.dto.request.AccountUpdateRequest;
import com.rentalroom.management.dto.response.AccountResponse;
import com.rentalroom.management.entity.Account;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceTest {

    private AccountRepository accountRepository;
    private PasswordEncoder passwordEncoder;
    private AccountService accountService;

    private Account account;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        accountService = new AccountService(accountRepository, passwordEncoder);

        account = new Account();
        account.setId(1L);
        account.setUsername("manager1");
        account.setPasswordHash("old-hash");
        account.setRole(Role.ADMIN_CAP_1);
        account.setActive(true);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    @Test
    void list_delegatesToRepositoryAndWrapsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(accountRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(account), pageable, 1));

        PageResponse<AccountResponse> response = accountService.list(Role.ADMIN_CAP_1, true, "manager", pageable);

        assertEquals(1, response.totalElements());
        assertEquals("manager1", response.content().get(0).username());
    }

    @Test
    void get_found_returnsResponse() {
        AccountResponse response = accountService.get(1L);
        assertEquals("manager1", response.username());
    }

    @Test
    void get_notFound_throwsNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> accountService.get(99L));
    }

    @Test
    void create_duplicateUsername_throwsConflict() {
        when(accountRepository.existsByUsername("manager1")).thenReturn(true);

        AccountCreateRequest request = new AccountCreateRequest(null, "manager1", "password", Role.ADMIN_CAP_1);
        assertThrows(BusinessException.class, () -> accountService.create(request));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void create_valid_encodesPasswordAndSavesActiveAccount() {
        when(accountRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");

        AccountCreateRequest request = new AccountCreateRequest(null, "newuser", "password123", Role.USER);
        AccountResponse response = assertDoesNotThrow(() -> accountService.create(request));

        assertEquals("newuser", response.username());
        assertTrue(response.active());

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertEquals("hashed-password", captor.getValue().getPasswordHash());
        assertTrue(captor.getValue().isActive());
    }

    @Test
    void update_notFound_throwsNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        AccountUpdateRequest request = new AccountUpdateRequest("newname", Role.USER);
        assertThrows(BusinessException.class, () -> accountService.update(99L, request));
    }

    @Test
    void update_usernameChangedToExistingOne_throwsConflict() {
        when(accountRepository.existsByUsername("taken")).thenReturn(true);
        AccountUpdateRequest request = new AccountUpdateRequest("taken", Role.USER);
        assertThrows(BusinessException.class, () -> accountService.update(1L, request));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void update_usernameUnchanged_doesNotCheckDuplicateAndSaves() {
        AccountUpdateRequest request = new AccountUpdateRequest("manager1", Role.ADMIN_TONG);
        AccountResponse response = assertDoesNotThrow(() -> accountService.update(1L, request));

        assertEquals(Role.ADMIN_TONG, response.role());
        verify(accountRepository, never()).existsByUsername(anyString());
    }

    @Test
    void listUnassigned_mapsAllToResponses() {
        Account unassigned = new Account();
        unassigned.setId(5L);
        unassigned.setUsername("free");
        when(accountRepository.findUnassigned()).thenReturn(List.of(unassigned));

        List<AccountResponse> result = accountService.listUnassigned();

        assertEquals(1, result.size());
        assertEquals("free", result.get(0).username());
    }

    @Test
    void changePassword_encodesAndSaves() {
        when(passwordEncoder.encode("newpass")).thenReturn("new-hash");

        accountService.changePassword(1L, "newpass");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertEquals("new-hash", captor.getValue().getPasswordHash());
    }

    @Test
    void setActive_updatesFlagAndSaves() {
        AccountResponse response = accountService.setActive(1L, false);
        assertEquals(false, response.active());
        verify(accountRepository, times(1)).save(any());
    }
}
