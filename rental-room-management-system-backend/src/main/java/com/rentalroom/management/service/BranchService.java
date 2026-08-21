package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.config.RedisConfig;
import com.rentalroom.management.dto.request.BranchRequest;
import com.rentalroom.management.dto.response.BranchDetailResponse;
import com.rentalroom.management.dto.response.BranchResponse;
import com.rentalroom.management.dto.response.RoomTypeSummaryResponse;
import com.rentalroom.management.entity.Account;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.AccountRepository;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.BranchRoomSummaryRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class BranchService {

    private final BranchRepository branchRepository;
    private final BranchRoomSummaryRepository branchRoomSummaryRepository;
    private final AccountRepository accountRepository;

    public BranchService(BranchRepository branchRepository,
                          BranchRoomSummaryRepository branchRoomSummaryRepository,
                          AccountRepository accountRepository) {
        this.branchRepository = branchRepository;
        this.branchRoomSummaryRepository = branchRoomSummaryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<BranchResponse> list(String search, Pageable pageable) {
        Specification<Branch> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("address")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PageResponse.from(branchRepository.findAll(spec, pageable).map(BranchResponse::from));
    }

    @Transactional(readOnly = true)
    public BranchDetailResponse get(Long id) {
        Branch branch = findEntity(id);
        return BranchDetailResponse.of(BranchResponse.from(branch), getRoomTypeSummaries(id));
    }

    @Cacheable(cacheNames = RedisConfig.CACHE_BRANCHES, key = "'room-type-summary:' + #branchId")
    public List<RoomTypeSummaryResponse> getRoomTypeSummaries(Long branchId) {
        return branchRoomSummaryRepository.findById_BranchId(branchId).stream()
                .map(RoomTypeSummaryResponse::from)
                .toList();
    }

    /** Call whenever a room's branch/type/status changes so the cached per-branch summary isn't stale. */
    @CacheEvict(cacheNames = RedisConfig.CACHE_BRANCHES, key = "'room-type-summary:' + #branchId")
    public void evictRoomTypeSummary(Long branchId) {
        // no-op body — eviction happens via the annotation
    }

    public BranchResponse create(BranchRequest request) {
        Branch branch = new Branch();
        branch.setName(request.name());
        branch.setAddress(request.address());
        branch.setManagerAccount(resolveManager(request.managerAccountId()));
        branch.setBankBin(request.bankBin());
        branch.setBankAccountNumber(request.bankAccountNumber());
        branch.setBankAccountName(request.bankAccountName());
        return BranchResponse.from(branchRepository.save(branch));
    }

    @CacheEvict(cacheNames = RedisConfig.CACHE_BRANCHES, key = "'room-type-summary:' + #id")
    public BranchResponse update(Long id, BranchRequest request) {
        Branch branch = findEntity(id);
        branch.setName(request.name());
        branch.setAddress(request.address());
        branch.setManagerAccount(resolveManager(request.managerAccountId()));
        branch.setBankBin(request.bankBin());
        branch.setBankAccountNumber(request.bankAccountNumber());
        branch.setBankAccountName(request.bankAccountName());
        return BranchResponse.from(branchRepository.save(branch));
    }

    private Account resolveManager(Long managerAccountId) {
        if (managerAccountId == null) {
            return null;
        }
        Account manager = accountRepository.findById(managerAccountId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy tài khoản quản lý"));
        if (manager.getRole() != Role.ADMIN_CAP_1) {
            throw new BusinessException(com.rentalroom.management.exception.ErrorCode.VALIDATION_ERROR,
                    "Người quản lý chi nhánh phải có vai trò ADMIN_CAP_1");
        }
        return manager;
    }

    private Branch findEntity(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy chi nhánh"));
    }
}
