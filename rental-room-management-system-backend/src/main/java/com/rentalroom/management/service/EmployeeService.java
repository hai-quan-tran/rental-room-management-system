package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.AccountCreateRequest;
import com.rentalroom.management.dto.request.EmployeeCreateRequest;
import com.rentalroom.management.dto.request.EmployeeUpdateRequest;
import com.rentalroom.management.dto.response.AccountResponse;
import com.rentalroom.management.dto.response.EmployeeResponse;
import com.rentalroom.management.entity.Account;
import com.rentalroom.management.entity.Employee;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.AccountRepository;
import com.rentalroom.management.repository.EmployeeRepository;
import com.rentalroom.management.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Employee management screen — ADMIN_TONG only. Each employee owns exactly 1 Account (see V5__employee.sql). */
@Service
@Transactional
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;

    public EmployeeService(EmployeeRepository employeeRepository, AccountRepository accountRepository,
                            AccountService accountService) {
        this.employeeRepository = employeeRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(String search, Pageable pageable) {
        Specification<Employee> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(root.get("idCardNumber"), "%" + search + "%")
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PageResponse.from(employeeRepository.findAll(spec, pageable).map(EmployeeResponse::from));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        return EmployeeResponse.from(findEntity(id));
    }

    public EmployeeResponse create(EmployeeCreateRequest request) {
        if (employeeRepository.existsByIdCardNumber(request.idCardNumber())) {
            throw BusinessException.conflict("CCCD đã tồn tại trong hệ thống");
        }

        boolean linksExisting = request.existingAccountId() != null;
        boolean createsNew = request.newAccount() != null;
        if (linksExisting == createsNew) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Phải chọn đúng 1 trong 2: tài khoản có sẵn hoặc thông tin tài khoản mới");
        }

        Account account;
        if (linksExisting) {
            account = accountRepository.findById(request.existingAccountId())
                    .orElseThrow(() -> BusinessException.notFound("Không tìm thấy tài khoản"));
            if (employeeRepository.existsByAccountId(account.getId())) {
                throw BusinessException.conflict("Tài khoản này đã gắn với một nhân viên khác");
            }
        } else {
            AccountResponse accountResponse = accountService.create(new AccountCreateRequest(
                    request.fullName(),
                    request.newAccount().username(),
                    request.newAccount().password(),
                    request.newAccount().role()
            ));
            account = accountRepository.getReferenceById(accountResponse.id());
        }
        // fullName is owned by the employee record, not the Account screen — keep the account in sync either way.
        account.setFullName(request.fullName());
        accountRepository.save(account);

        Employee employee = new Employee();
        applyRequest(employee, request.fullName(), request.dateOfBirth(), request.idCardNumber(),
                request.phoneNumber(), request.email());
        employee.setAccount(account);
        employee.setCreatedBy(SecurityUtils.currentUser().userId());
        employee.setUpdatedBy(SecurityUtils.currentUser().userId());
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = findEntity(id);
        if (!employee.getIdCardNumber().equals(request.idCardNumber())
                && employeeRepository.existsByIdCardNumberAndIdNot(request.idCardNumber(), id)) {
            throw BusinessException.conflict("CCCD đã tồn tại trong hệ thống");
        }
        applyRequest(employee, request.fullName(), request.dateOfBirth(), request.idCardNumber(),
                request.phoneNumber(), request.email());
        employee.setUpdatedBy(SecurityUtils.currentUser().userId());
        // Keep the linked account's fullName in sync — it is owned by this record, not the Account screen.
        employee.getAccount().setFullName(request.fullName());
        accountRepository.save(employee.getAccount());
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    public void delete(Long id) {
        Employee employee = findEntity(id);
        // Deactivate rather than hard-delete the linked account — preserves audit/history that
        // may reference account_id elsewhere, matching the existing Account soft-delete convention.
        accountService.setActive(employee.getAccount().getId(), false);
        employeeRepository.delete(employee);
    }

    private void applyRequest(Employee employee, String fullName, LocalDate dateOfBirth,
                               String idCardNumber, String phoneNumber, String email) {
        employee.setFullName(fullName);
        employee.setDateOfBirth(dateOfBirth);
        employee.setIdCardNumber(idCardNumber);
        employee.setPhoneNumber(phoneNumber);
        employee.setEmail(email);
    }

    private Employee findEntity(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy nhân viên"));
    }
}
