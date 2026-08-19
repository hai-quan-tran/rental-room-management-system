package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.EmployeeAccountRequest;
import com.rentalroom.management.dto.request.EmployeeCreateRequest;
import com.rentalroom.management.dto.request.EmployeeUpdateRequest;
import com.rentalroom.management.dto.response.AccountResponse;
import com.rentalroom.management.dto.response.EmployeeResponse;
import com.rentalroom.management.entity.Account;
import com.rentalroom.management.entity.Employee;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.AccountRepository;
import com.rentalroom.management.repository.EmployeeRepository;
import com.rentalroom.management.security.SecurityUtils;
import com.rentalroom.management.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployeeServiceTest {

    private EmployeeRepository employeeRepository;
    private AccountRepository accountRepository;
    private AccountService accountService;
    private EmployeeService employeeService;

    private MockedStatic<SecurityUtils> securityUtils;

    private Account account;
    private Employee employee;

    @BeforeEach
    void setUp() {
        employeeRepository = mock(EmployeeRepository.class);
        accountRepository = mock(AccountRepository.class);
        accountService = mock(AccountService.class);

        employeeService = new EmployeeService(employeeRepository, accountRepository, accountService);

        account = new Account();
        account.setId(200L);
        account.setUsername("nvA");
        account.setRole(Role.USER);
        account.setActive(true);

        employee = new Employee();
        employee.setId(300L);
        employee.setFullName("Nguyen Van A");
        employee.setDateOfBirth(LocalDate.of(1995, 5, 20));
        employee.setIdCardNumber("079095001234");
        employee.setPhoneNumber("0912345678");
        employee.setEmail("nva@example.com");
        employee.setAccount(account);

        when(employeeRepository.findById(300L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        securityUtils = Mockito.mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::currentUser)
                .thenReturn(new UserPrincipal(1L, "admin", Role.ADMIN_TONG, List.of()));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private EmployeeCreateRequest createRequestLinkingExisting(Long existingAccountId) {
        return new EmployeeCreateRequest("Le Thi C", LocalDate.of(1998, 3, 3), "079098005678",
                "0987654321", "lethic@example.com", existingAccountId, null);
    }

    private EmployeeCreateRequest createRequestWithNewAccount() {
        return new EmployeeCreateRequest("Le Thi C", LocalDate.of(1998, 3, 3), "079098005678",
                "0987654321", "lethic@example.com", null,
                new EmployeeAccountRequest("lethic", "password123", Role.USER));
    }

    @Test
    void list_returnsMappedPage() {
        when(employeeRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee)));

        PageResponse<EmployeeResponse> response = employeeService.list("A", PageRequest.of(0, 10));

        assertEquals(1, response.content().size());
        assertEquals(300L, response.content().get(0).id());
        assertEquals(200L, response.content().get(0).accountId());
    }

    @Test
    void get_found_returnsResponse() {
        EmployeeResponse response = employeeService.get(300L);
        assertEquals("Nguyen Van A", response.fullName());
    }

    @Test
    void get_notFound_throwsNotFound() {
        when(employeeRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> employeeService.get(999L));
    }

    @Test
    void create_duplicateIdCard_throwsConflict() {
        when(employeeRepository.existsByIdCardNumber("079098005678")).thenReturn(true);
        assertThrows(BusinessException.class, () -> employeeService.create(createRequestLinkingExisting(200L)));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void create_neitherAccountOptionProvided_throwsValidationError() {
        EmployeeCreateRequest request = new EmployeeCreateRequest("Le Thi C", LocalDate.of(1998, 3, 3),
                "079098005678", "0987654321", "lethic@example.com", null, null);
        assertThrows(BusinessException.class, () -> employeeService.create(request));
    }

    @Test
    void create_bothAccountOptionsProvided_throwsValidationError() {
        EmployeeCreateRequest request = new EmployeeCreateRequest("Le Thi C", LocalDate.of(1998, 3, 3),
                "079098005678", "0987654321", "lethic@example.com", 200L,
                new EmployeeAccountRequest("lethic", "password123", Role.USER));
        assertThrows(BusinessException.class, () -> employeeService.create(request));
    }

    @Test
    void create_existingAccountNotFound_throwsNotFound() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> employeeService.create(createRequestLinkingExisting(999L)));
    }

    @Test
    void create_existingAccountAlreadyLinkedToOtherEmployee_throwsConflict() {
        Account unassigned = new Account();
        unassigned.setId(200L);
        when(accountRepository.findById(200L)).thenReturn(Optional.of(unassigned));
        when(employeeRepository.existsByAccountId(200L)).thenReturn(true);

        assertThrows(BusinessException.class, () -> employeeService.create(createRequestLinkingExisting(200L)));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void create_linkExistingUnassignedAccount_savesEmployeeAndSyncsFullName() {
        Account unassigned = new Account();
        unassigned.setId(200L);
        when(accountRepository.findById(200L)).thenReturn(Optional.of(unassigned));
        when(employeeRepository.existsByAccountId(200L)).thenReturn(false);

        EmployeeResponse response = assertDoesNotThrow(() -> employeeService.create(createRequestLinkingExisting(200L)));

        assertEquals("Le Thi C", unassigned.getFullName());
        verify(accountRepository).save(unassigned);
        verify(accountService, never()).create(any());
        assertEquals("Le Thi C", response.fullName());
    }

    @Test
    void create_newAccountInline_createsAccountThenEmployee() {
        AccountResponse createdAccount = new AccountResponse(400L, "Le Thi C", "lethic", Role.USER, true,
                LocalDateTime.now(), LocalDateTime.now());
        when(accountService.create(any())).thenReturn(createdAccount);
        Account reference = new Account();
        reference.setId(400L);
        when(accountRepository.getReferenceById(400L)).thenReturn(reference);

        EmployeeResponse response = assertDoesNotThrow(() -> employeeService.create(createRequestWithNewAccount()));

        verify(accountService).create(any());
        assertEquals("Le Thi C", reference.getFullName());
        assertEquals("Le Thi C", response.fullName());
    }

    @Test
    void update_duplicateIdCardOnAnotherEmployee_throwsConflict() {
        EmployeeUpdateRequest request = new EmployeeUpdateRequest("Nguyen Van A", LocalDate.of(1995, 5, 20),
                "079095009999", "0912345678", "nva@example.com");
        when(employeeRepository.existsByIdCardNumberAndIdNot("079095009999", 300L)).thenReturn(true);

        assertThrows(BusinessException.class, () -> employeeService.update(300L, request));
    }

    @Test
    void update_valid_updatesEmployeeAndSyncsAccountFullName() {
        EmployeeUpdateRequest request = new EmployeeUpdateRequest("Nguyen Van A Updated", LocalDate.of(1995, 5, 20),
                "079095001234", "0912345678", "nva.updated@example.com");

        EmployeeResponse response = assertDoesNotThrow(() -> employeeService.update(300L, request));

        assertEquals("Nguyen Van A Updated", response.fullName());
        assertEquals("Nguyen Van A Updated", account.getFullName());
        verify(accountRepository).save(account);
    }

    @Test
    void delete_deactivatesLinkedAccountAndDeletesEmployee() {
        employeeService.delete(300L);

        verify(accountService).setActive(200L, false);
        verify(employeeRepository).delete(employee);
    }
}
