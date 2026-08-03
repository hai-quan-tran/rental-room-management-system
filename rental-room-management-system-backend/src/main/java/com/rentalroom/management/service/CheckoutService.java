package com.rentalroom.management.service;

import com.rentalroom.management.dto.request.CheckoutItemRequest;
import com.rentalroom.management.dto.request.CheckoutRequest;
import com.rentalroom.management.dto.response.CheckoutResponse;
import com.rentalroom.management.dto.response.DebtRecordResponse;
import com.rentalroom.management.entity.CheckoutChecklist;
import com.rentalroom.management.entity.CheckoutChecklistItem;
import com.rentalroom.management.entity.Contract;
import com.rentalroom.management.entity.DebtRecord;
import com.rentalroom.management.entity.Room;
import com.rentalroom.management.entity.RoomTypeHandoverItem;
import com.rentalroom.management.enums.ChecklistItemStatus;
import com.rentalroom.management.enums.ContractStatus;
import com.rentalroom.management.enums.DebtStatus;
import com.rentalroom.management.enums.RoomStatus;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.CheckoutChecklistRepository;
import com.rentalroom.management.repository.ContractRepository;
import com.rentalroom.management.repository.DebtRecordRepository;
import com.rentalroom.management.repository.RoomRepository;
import com.rentalroom.management.repository.RoomTypeHandoverItemRepository;
import com.rentalroom.management.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class CheckoutService {

    private final ContractRepository contractRepository;
    private final RoomRepository roomRepository;
    private final RoomTypeHandoverItemRepository handoverItemRepository;
    private final CheckoutChecklistRepository checkoutChecklistRepository;
    private final DebtRecordRepository debtRecordRepository;
    private final BillingService billingService;
    private final BranchService branchService;

    public CheckoutService(ContractRepository contractRepository,
                            RoomRepository roomRepository,
                            RoomTypeHandoverItemRepository handoverItemRepository,
                            CheckoutChecklistRepository checkoutChecklistRepository,
                            DebtRecordRepository debtRecordRepository,
                            BillingService billingService,
                            BranchService branchService) {
        this.contractRepository = contractRepository;
        this.roomRepository = roomRepository;
        this.handoverItemRepository = handoverItemRepository;
        this.checkoutChecklistRepository = checkoutChecklistRepository;
        this.debtRecordRepository = debtRecordRepository;
        this.billingService = billingService;
        this.branchService = branchService;
    }

    @Transactional(readOnly = true)
    public CheckoutResponse get(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy hợp đồng"));
        SecurityUtils.assertCanAccessBranch(contract.getRoom().getBranch().getId());

        CheckoutChecklist checklist = checkoutChecklistRepository.findByContractId(contractId)
                .orElseThrow(() -> BusinessException.notFound("Hợp đồng này chưa có checklist trả phòng"));
        DebtRecordResponse debtRecordResponse = debtRecordRepository.findByChecklistId(checklist.getId())
                .map(DebtRecordResponse::from)
                .orElse(null);
        return CheckoutResponse.of(checklist, debtRecordResponse);
    }

    public CheckoutResponse checkout(Long contractId, CheckoutRequest request) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy hợp đồng"));
        Room room = contract.getRoom();
        SecurityUtils.assertCanAccessBranch(room.getBranch().getId());

        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw BusinessException.conflict("Hợp đồng đã kết thúc trước đó");
        }
        if (request.checkoutDate().isBefore(contract.getStartDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày trả phòng không được trước ngày bắt đầu hợp đồng");
        }

        for (CheckoutItemRequest item : request.items()) {
            if (item.status() == ChecklistItemStatus.CON_NGUYEN && item.deductionAmount().signum() != 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Vật dụng còn nguyên thì không được có tiền trừ");
            }
        }

        contract.setEndDate(request.checkoutDate());
        contractRepository.save(contract);

        // Recompute the checkout month's rent now that the true end date is known, then fold in
        // every bill still carrying a balance (spec: "Công nợ tiền thuê/chi phí phát sinh còn lại").
        billingService.upsertRentForCheckoutMonth(contract);
        BigDecimal outstandingBillDebt = billingService.totalOutstandingBillDebt(contractId);

        BigDecimal itemsDeduction = request.items().stream()
                .map(CheckoutItemRequest::deductionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDeduction = itemsDeduction.add(outstandingBillDebt);
        BigDecimal depositRefund = contract.getDepositAmount().subtract(totalDeduction).max(BigDecimal.ZERO);

        CheckoutChecklist checklist = new CheckoutChecklist();
        checklist.setContract(contract);
        checklist.setCheckedBy(SecurityUtils.currentUser().userId());
        checklist.setDepositAmount(contract.getDepositAmount());
        checklist.setDeductionAmount(totalDeduction);
        checklist.setDepositRefundAmount(depositRefund);
        checklist.setNote(request.note());

        for (CheckoutItemRequest itemRequest : request.items()) {
            RoomTypeHandoverItem handoverItem = handoverItemRepository.findById(itemRequest.roomTypeHandoverItemId())
                    .orElseThrow(() -> BusinessException.notFound("Không tìm thấy vật dụng bàn giao"));
            CheckoutChecklistItem checklistItem = new CheckoutChecklistItem();
            checklistItem.setChecklist(checklist);
            checklistItem.setRoomTypeHandoverItem(handoverItem);
            checklistItem.setStatus(itemRequest.status());
            checklistItem.setDeductionAmount(itemRequest.deductionAmount());
            checklistItem.setNote(itemRequest.note());
            checklist.getItems().add(checklistItem);
        }
        CheckoutChecklist savedChecklist = checkoutChecklistRepository.save(checklist);

        DebtRecordResponse debtRecordResponse = null;
        if (totalDeduction.compareTo(contract.getDepositAmount()) > 0) {
            BigDecimal excess = totalDeduction.subtract(contract.getDepositAmount());
            DebtRecord debtRecord = new DebtRecord();
            debtRecord.setContract(contract);
            debtRecord.setChecklist(savedChecklist);
            debtRecord.setAmount(excess);
            debtRecord.setReason("Vượt cọc khi trả phòng (hư hỏng/mất vật dụng và/hoặc còn nợ tiền thuê, chi phí phát sinh)");
            debtRecord.setStatus(DebtStatus.CHUA_THU);
            debtRecordResponse = DebtRecordResponse.from(debtRecordRepository.save(debtRecord));
        }

        contract.setStatus(ContractStatus.ENDED);
        contractRepository.save(contract);

        room.setStatus(RoomStatus.TRONG);
        roomRepository.save(room);
        branchService.evictRoomTypeSummary(room.getBranch().getId());

        return CheckoutResponse.of(savedChecklist, debtRecordResponse);
    }
}
