package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.TenantRequest;
import com.rentalroom.management.dto.response.TenantRentalHistoryResponse;
import com.rentalroom.management.dto.response.TenantResponse;
import com.rentalroom.management.entity.Tenant;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.ContractTenantRepository;
import com.rentalroom.management.repository.TenantRepository;
import com.rentalroom.management.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;
    private final ContractTenantRepository contractTenantRepository;

    public TenantService(TenantRepository tenantRepository, ContractTenantRepository contractTenantRepository) {
        this.tenantRepository = tenantRepository;
        this.contractTenantRepository = contractTenantRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantResponse> list(String search, Pageable pageable) {
        Specification<Tenant> spec = (root, query, cb) -> {
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
        return PageResponse.from(tenantRepository.findAll(spec, pageable).map(TenantResponse::from));
    }

    @Transactional(readOnly = true)
    public TenantResponse get(Long id) {
        return TenantResponse.from(findEntity(id));
    }

    @Transactional(readOnly = true)
    public List<TenantRentalHistoryResponse> getRentalHistory(Long id) {
        findEntity(id);
        return contractTenantRepository.findById_TenantIdOrderByJoinedAtDesc(id).stream()
                .map(TenantRentalHistoryResponse::from)
                .toList();
    }

    public TenantResponse create(TenantRequest request) {
        if (tenantRepository.existsByIdCardNumber(request.idCardNumber())) {
            throw BusinessException.conflict("CCCD đã tồn tại trong hệ thống");
        }
        Tenant tenant = new Tenant();
        applyRequest(tenant, request);
        tenant.setCreatedBy(SecurityUtils.currentUser().userId());
        tenant.setUpdatedBy(SecurityUtils.currentUser().userId());
        return TenantResponse.from(tenantRepository.save(tenant));
    }

    public TenantResponse update(Long id, TenantRequest request) {
        Tenant tenant = findEntity(id);
        if (!tenant.getIdCardNumber().equals(request.idCardNumber())
                && tenantRepository.existsByIdCardNumberAndIdNot(request.idCardNumber(), id)) {
            throw BusinessException.conflict("CCCD đã tồn tại trong hệ thống");
        }
        applyRequest(tenant, request);
        tenant.setUpdatedBy(SecurityUtils.currentUser().userId());
        return TenantResponse.from(tenantRepository.save(tenant));
    }

    public void delete(Long id) {
        findEntity(id);
        if (!contractTenantRepository.findById_TenantIdOrderByJoinedAtDesc(id).isEmpty()) {
            throw BusinessException.conflict("Không thể xóa người thuê đã có lịch sử hợp đồng");
        }
        tenantRepository.deleteById(id);
    }

    private void applyRequest(Tenant tenant, TenantRequest request) {
        tenant.setFullName(request.fullName());
        tenant.setDateOfBirth(request.dateOfBirth());
        tenant.setIdCardNumber(request.idCardNumber());
        tenant.setPhoneNumber(request.phoneNumber());
        tenant.setEmail(request.email());
    }

    private Tenant findEntity(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy người thuê"));
    }
}
