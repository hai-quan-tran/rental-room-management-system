package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.response.DebtRecordResponse;
import com.rentalroom.management.entity.DebtRecord;
import com.rentalroom.management.enums.DebtStatus;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.repository.DebtRecordRepository;
import com.rentalroom.management.security.SecurityUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class DebtRecordService {

    private final DebtRecordRepository debtRecordRepository;

    public DebtRecordService(DebtRecordRepository debtRecordRepository) {
        this.debtRecordRepository = debtRecordRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<DebtRecordResponse> list(DebtStatus status, Pageable pageable) {
        DebtStatus effectiveStatus = status != null ? status : DebtStatus.CHUA_THU;
        var user = SecurityUtils.currentUser();

        var page = user.role() == Role.ADMIN_CAP_1
                ? debtRecordRepository.findByStatusAndContract_Room_BranchIdIn(effectiveStatus, user.branchIds(), pageable)
                : debtRecordRepository.findByStatus(effectiveStatus, pageable);

        return PageResponse.from(page.map(DebtRecordResponse::from));
    }

    public DebtRecordResponse collect(Long id, BigDecimal collectedAmount) {
        DebtRecord debtRecord = debtRecordRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy công nợ"));
        SecurityUtils.assertCanAccessBranch(debtRecord.getContract().getRoom().getBranch().getId());

        BigDecimal newCollected = debtRecord.getCollectedAmount().add(collectedAmount);
        if (newCollected.compareTo(debtRecord.getAmount()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số tiền thu vượt quá số tiền còn nợ");
        }
        debtRecord.setCollectedAmount(newCollected);
        if (newCollected.compareTo(debtRecord.getAmount()) == 0) {
            debtRecord.setStatus(DebtStatus.DA_THU);
        }
        return DebtRecordResponse.from(debtRecordRepository.save(debtRecord));
    }
}
