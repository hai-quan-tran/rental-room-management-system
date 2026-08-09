package com.rentalroom.management.service;

import com.rentalroom.management.dto.request.UtilityRateRequest;
import com.rentalroom.management.dto.response.UtilityRateResponse;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.ExtraFeeCategory;
import com.rentalroom.management.entity.UtilityRate;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.ExtraFeeCategoryRepository;
import com.rentalroom.management.repository.UtilityRateRepository;
import com.rentalroom.management.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Per-branch electricity/water unit price, versioned by effective date — add-only, never updated/deleted. */
@Service
@Transactional
public class UtilityRateService {

    private final UtilityRateRepository utilityRateRepository;
    private final BranchRepository branchRepository;
    private final ExtraFeeCategoryRepository extraFeeCategoryRepository;

    public UtilityRateService(UtilityRateRepository utilityRateRepository,
                               BranchRepository branchRepository,
                               ExtraFeeCategoryRepository extraFeeCategoryRepository) {
        this.utilityRateRepository = utilityRateRepository;
        this.branchRepository = branchRepository;
        this.extraFeeCategoryRepository = extraFeeCategoryRepository;
    }

    @Transactional(readOnly = true)
    public List<UtilityRateResponse> list(Long branchId) {
        SecurityUtils.assertCanAccessBranch(branchId);
        return utilityRateRepository.findByBranchIdOrderByExtraFeeCategoryIdAscEffectiveFromDesc(branchId).stream()
                .map(UtilityRateResponse::from)
                .toList();
    }

    public UtilityRateResponse create(Long branchId, UtilityRateRequest request) {
        SecurityUtils.assertCanAccessBranch(branchId);
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy chi nhánh"));
        ExtraFeeCategory category = extraFeeCategoryRepository.findById(request.extraFeeCategoryId())
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy danh mục chi phí"));
        if (!category.isMetered()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Danh mục \"" + category.getName() + "\" không phải loại chi phí tính theo chỉ số");
        }
        if (utilityRateRepository.existsByBranchIdAndExtraFeeCategoryIdAndEffectiveFrom(
                branchId, request.extraFeeCategoryId(), request.effectiveFrom())) {
            throw BusinessException.conflict("Đã có đơn giá cho danh mục này với đúng ngày hiệu lực đó");
        }

        UtilityRate rate = new UtilityRate();
        rate.setBranch(branch);
        rate.setExtraFeeCategory(category);
        rate.setUnitPrice(request.unitPrice());
        rate.setEffectiveFrom(request.effectiveFrom());
        return UtilityRateResponse.from(utilityRateRepository.save(rate));
    }

    /** The unit price in effect for a given billing month (referenceDate = first day of that month) — empty if none configured yet. */
    @Transactional(readOnly = true)
    Optional<UtilityRate> findCurrentRate(Long branchId, Long extraFeeCategoryId, LocalDate referenceDate) {
        return utilityRateRepository
                .findTopByBranchIdAndExtraFeeCategoryIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        branchId, extraFeeCategoryId, referenceDate);
    }
}
