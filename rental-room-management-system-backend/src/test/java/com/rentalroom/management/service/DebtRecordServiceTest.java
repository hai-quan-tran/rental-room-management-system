package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.response.DebtRecordResponse;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Contract;
import com.rentalroom.management.entity.DebtRecord;
import com.rentalroom.management.entity.Room;
import com.rentalroom.management.enums.DebtStatus;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.DebtRecordRepository;
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

import java.math.BigDecimal;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DebtRecordServiceTest {

    private DebtRecordRepository debtRecordRepository;
    private DebtRecordService debtRecordService;

    private MockedStatic<SecurityUtils> securityUtils;

    private DebtRecord debtRecord;

    @BeforeEach
    void setUp() {
        debtRecordRepository = mock(DebtRecordRepository.class);
        debtRecordService = new DebtRecordService(debtRecordRepository);

        Branch branch = new Branch();
        branch.setId(1L);

        Room room = new Room();
        room.setId(50L);
        room.setBranch(branch);
        room.setRoomCode("P101");

        Contract contract = new Contract();
        contract.setId(500L);
        contract.setRoom(room);

        debtRecord = new DebtRecord();
        debtRecord.setId(700L);
        debtRecord.setContract(contract);
        debtRecord.setAmount(new BigDecimal("1000000"));
        debtRecord.setCollectedAmount(new BigDecimal("200000"));
        debtRecord.setReason("Vượt cọc");
        debtRecord.setStatus(DebtStatus.CHUA_THU);

        when(debtRecordRepository.findById(700L)).thenReturn(Optional.of(debtRecord));
        when(debtRecordRepository.save(any(DebtRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        securityUtils = Mockito.mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::currentUser)
                .thenReturn(new UserPrincipal(1L, "admin", Role.ADMIN_TONG, List.of()));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void list_statusNull_defaultsToChuaThu_adminTongUsesUnscopedQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        when(debtRecordRepository.findByStatus(DebtStatus.CHUA_THU, pageable))
                .thenReturn(new PageImpl<>(List.of(debtRecord), pageable, 1));

        PageResponse<DebtRecordResponse> response = debtRecordService.list(null, pageable);

        assertEquals(1, response.totalElements());
        verify(debtRecordRepository, never()).findByStatusAndContract_Room_BranchIdIn(any(), any(), any());
    }

    @Test
    void list_explicitStatus_adminCap1_scopesToManagedBranches() {
        securityUtils.when(SecurityUtils::currentUser)
                .thenReturn(new UserPrincipal(2L, "manager", Role.ADMIN_CAP_1, List.of(1L, 2L)));
        Pageable pageable = PageRequest.of(0, 10);
        when(debtRecordRepository.findByStatusAndContract_Room_BranchIdIn(eq(DebtStatus.DA_THU), eq(List.of(1L, 2L)), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(debtRecord), pageable, 1));

        PageResponse<DebtRecordResponse> response = debtRecordService.list(DebtStatus.DA_THU, pageable);

        assertEquals(1, response.totalElements());
        verify(debtRecordRepository, never()).findByStatus(any(), any());
    }

    @Test
    void collect_notFound_throwsNotFound() {
        when(debtRecordRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> debtRecordService.collect(999L, new BigDecimal("100000")));
    }

    @Test
    void collect_accessDenied_throwsAccessDenied() {
        securityUtils.when(() -> SecurityUtils.assertCanAccessBranch(anyLong()))
                .thenThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không quản lý chi nhánh này"));

        assertThrows(BusinessException.class, () -> debtRecordService.collect(700L, new BigDecimal("100000")));
        verify(debtRecordRepository, never()).save(any());
    }

    @Test
    void collect_amountExceedsRemainingDebt_throwsValidationError() {
        assertThrows(BusinessException.class, () -> debtRecordService.collect(700L, new BigDecimal("900000")));
        verify(debtRecordRepository, never()).save(any());
    }

    @Test
    void collect_exactRemainingAmount_marksAsDaThu() {
        DebtRecordResponse response = assertDoesNotThrow(() -> debtRecordService.collect(700L, new BigDecimal("800000")));

        assertEquals(new BigDecimal("1000000"), response.collectedAmount());
        assertEquals(DebtStatus.DA_THU, response.status());
    }

    @Test
    void collect_partialAmount_updatesCollectedAmountKeepsChuaThu() {
        DebtRecordResponse response = assertDoesNotThrow(() -> debtRecordService.collect(700L, new BigDecimal("300000")));

        assertEquals(new BigDecimal("500000"), response.collectedAmount());
        assertEquals(DebtStatus.CHUA_THU, response.status());
    }
}
