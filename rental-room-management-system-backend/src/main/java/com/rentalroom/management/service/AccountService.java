package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.AccountCreateRequest;
import com.rentalroom.management.dto.request.AccountUpdateRequest;
import com.rentalroom.management.dto.response.AccountResponse;
import com.rentalroom.management.entity.Account;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.AccountRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> list(Role role, Boolean active, String search, Pageable pageable) {
        Specification<Account> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("username")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PageResponse.from(accountRepository.findAll(spec, pageable).map(AccountResponse::from));
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long id) {
        return AccountResponse.from(findEntity(id));
    }

    public AccountResponse create(AccountCreateRequest request) {
        if (accountRepository.existsByUsername(request.username())) {
            throw BusinessException.conflict("Tên đăng nhập đã tồn tại");
        }
        Account account = new Account();
        account.setFullName(request.fullName());
        account.setUsername(request.username());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole(request.role());
        account.setActive(true);
        return AccountResponse.from(accountRepository.save(account));
    }

    public AccountResponse update(Long id, AccountUpdateRequest request) {
        Account account = findEntity(id);
        if (!account.getUsername().equals(request.username())
                && accountRepository.existsByUsername(request.username())) {
            throw BusinessException.conflict("Tên đăng nhập đã tồn tại");
        }
        account.setUsername(request.username());
        account.setRole(request.role());
        return AccountResponse.from(accountRepository.save(account));
    }

    /** Accounts not yet linked to any Employee — candidates for the "link existing account" flow on the Employee screen. */
    @Transactional(readOnly = true)
    public List<AccountResponse> listUnassigned() {
        return accountRepository.findUnassigned().stream().map(AccountResponse::from).toList();
    }

    public void changePassword(Long id, String newPassword) {
        Account account = findEntity(id);
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
    }

    public AccountResponse setActive(Long id, boolean active) {
        Account account = findEntity(id);
        account.setActive(active);
        return AccountResponse.from(accountRepository.save(account));
    }

    private Account findEntity(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy tài khoản"));
    }
}
