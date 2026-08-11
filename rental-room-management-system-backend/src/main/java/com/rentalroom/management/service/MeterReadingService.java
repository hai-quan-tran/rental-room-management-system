package com.rentalroom.management.service;

import com.rentalroom.management.common.util.MoneyUtils;
import com.rentalroom.management.dto.request.MeterReadingRequest;
import com.rentalroom.management.dto.response.MeterReadingCellResponse;
import com.rentalroom.management.dto.response.MeterReadingGridRowResponse;
import com.rentalroom.management.dto.response.MeterReadingResponse;
import com.rentalroom.management.entity.Contract;
import com.rentalroom.management.entity.ExtraFeeCategory;
import com.rentalroom.management.entity.MeterReading;
import com.rentalroom.management.entity.Room;
import com.rentalroom.management.entity.UtilityRate;
import com.rentalroom.management.enums.BillSyncStatus;
import com.rentalroom.management.enums.ContractStatus;
import com.rentalroom.management.enums.PaymentStatus;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.ContractRepository;
import com.rentalroom.management.repository.ExtraFeeCategoryRepository;
import com.rentalroom.management.repository.MeterReadingRepository;
import com.rentalroom.management.repository.MonthlyBillRepository;
import com.rentalroom.management.repository.RoomRepository;
import com.rentalroom.management.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Per-room-per-month electricity/water meter readings, and the grid used to enter them branch-wide for a given month. */
@Service
@Transactional
public class MeterReadingService {

    private final MeterReadingRepository meterReadingRepository;
    private final RoomRepository roomRepository;
    private final ExtraFeeCategoryRepository extraFeeCategoryRepository;
    private final ContractRepository contractRepository;
    private final MonthlyBillRepository monthlyBillRepository;
    private final UtilityRateService utilityRateService;
    private final BillingService billingService;
    private final EntityManager entityManager;

    public MeterReadingService(MeterReadingRepository meterReadingRepository,
                                RoomRepository roomRepository,
                                ExtraFeeCategoryRepository extraFeeCategoryRepository,
                                ContractRepository contractRepository,
                                MonthlyBillRepository monthlyBillRepository,
                                UtilityRateService utilityRateService,
                                BillingService billingService,
                                EntityManager entityManager) {
        this.meterReadingRepository = meterReadingRepository;
        this.roomRepository = roomRepository;
        this.extraFeeCategoryRepository = extraFeeCategoryRepository;
        this.contractRepository = contractRepository;
        this.monthlyBillRepository = monthlyBillRepository;
        this.utilityRateService = utilityRateService;
        this.billingService = billingService;
        this.entityManager = entityManager;
    }

    /** 1 row per room with an ACTIVE contract in the branch, 1 cell per metered category — vacant rooms have no bill, so no reading to enter. */
    @Transactional(readOnly = true)
    public List<MeterReadingGridRowResponse> listGrid(Long branchId, Integer billYear, Integer billMonth) {
        SecurityUtils.assertCanAccessBranch(branchId);
        List<ExtraFeeCategory> categories = extraFeeCategoryRepository.findByMeteredTrue();
        LocalDate referenceDate = LocalDate.of(billYear, billMonth, 1);

        List<MeterReadingGridRowResponse> rows = new ArrayList<>();
        for (Contract contract : contractRepository.findByStatusAndRoom_BranchIdIn(ContractStatus.ACTIVE, List.of(branchId))) {
            Room room = contract.getRoom();
            List<MeterReadingCellResponse> cells = new ArrayList<>();
            for (ExtraFeeCategory category : categories) {
                cells.add(buildCell(room, category, billYear, billMonth, referenceDate));
            }
            rows.add(new MeterReadingGridRowResponse(room.getId(), room.getRoomCode(), contract.getId(), cells));
        }
        return rows;
    }

    public MeterReadingResponse upsertReading(Long roomId, MeterReadingRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy phòng"));
        SecurityUtils.assertCanAccessBranch(room.getBranch().getId());

        ExtraFeeCategory category = extraFeeCategoryRepository.findById(request.extraFeeCategoryId())
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy danh mục chi phí"));
        if (!category.isMetered()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Danh mục \"" + category.getName() + "\" không phải loại chi phí tính theo chỉ số");
        }
        if (isBillFullyPaid(roomId, request.billYear(), request.billMonth())) {
            throw BusinessException.conflict("Hóa đơn tháng này đã thanh toán đủ, không thể sửa chỉ số điện nước");
        }

        MeterReading reading = meterReadingRepository
                .findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(roomId, category.getId(), request.billYear(), request.billMonth())
                .orElseGet(MeterReading::new);

        BigDecimal oldReading = request.oldReading();
        if (oldReading == null) {
            oldReading = findPreviousReading(roomId, category.getId(), request.billYear(), request.billMonth())
                    .map(MeterReading::getNewReading)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "Đây là lần ghi chỉ số đầu tiên của phòng cho danh mục này — cần nhập chỉ số cũ"));
        }
        if (request.newReading().compareTo(oldReading) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ số mới không được nhỏ hơn chỉ số cũ");
        }

        reading.setRoom(room);
        reading.setExtraFeeCategory(category);
        reading.setBillYear(request.billYear());
        reading.setBillMonth(request.billMonth());
        reading.setOldReading(oldReading);
        reading.setNewReading(request.newReading());
        reading.setNote(request.note());
        reading.setRecordedBy(SecurityUtils.currentUser().userId());

        MeterReading saved = meterReadingRepository.save(reading);
        entityManager.flush();
        entityManager.refresh(saved);

        LocalDate referenceDate = LocalDate.of(request.billYear(), request.billMonth(), 1);
        Optional<UtilityRate> rate = utilityRateService.findCurrentRate(room.getBranch().getId(), category.getId(), referenceDate);
        BigDecimal unitPrice = rate.map(UtilityRate::getUnitPrice).orElse(null);
        BigDecimal amount = unitPrice != null
                ? MoneyUtils.roundToWholeVnd(saved.getConsumption().multiply(unitPrice))
                : BigDecimal.ZERO;
        String syncNote = unitPrice != null
                ? "Tự động: chỉ số " + saved.getOldReading() + "→" + saved.getNewReading() + " × " + unitPrice + "đ"
                : "Đã có chỉ số nhưng chưa cấu hình đơn giá";

        BillSyncStatus billSyncStatus = billingService.syncMeteredExtraFeeItem(
                roomId, request.billYear(), request.billMonth(), category, amount, syncNote);

        return MeterReadingResponse.from(saved, unitPrice, amount, billSyncStatus);
    }

    private MeterReadingCellResponse buildCell(Room room, ExtraFeeCategory category, Integer billYear, Integer billMonth, LocalDate referenceDate) {
        BigDecimal unitPrice = utilityRateService.findCurrentRate(room.getBranch().getId(), category.getId(), referenceDate)
                .map(UtilityRate::getUnitPrice)
                .orElse(null);
        boolean billFullyPaid = isBillFullyPaid(room.getId(), billYear, billMonth);

        Optional<MeterReading> current = meterReadingRepository
                .findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(room.getId(), category.getId(), billYear, billMonth);
        if (current.isPresent()) {
            MeterReading reading = current.get();
            BigDecimal amount = unitPrice != null
                    ? MoneyUtils.roundToWholeVnd(reading.getConsumption().multiply(unitPrice))
                    : null;
            return new MeterReadingCellResponse(category.getId(), category.getName(), category.getUnit(),
                    reading.getOldReading(), reading.getNewReading(), reading.getConsumption(), unitPrice, amount,
                    reading.getNote(), billFullyPaid);
        }

        BigDecimal previousReading = findPreviousReading(room.getId(), category.getId(), billYear, billMonth)
                .map(MeterReading::getNewReading)
                .orElse(null);
        return new MeterReadingCellResponse(category.getId(), category.getName(), category.getUnit(),
                previousReading, null, null, unitPrice, null, null, billFullyPaid);
    }

    /** A room+month is locked for reading edits once its (unambiguous) bill is fully paid. */
    private boolean isBillFullyPaid(Long roomId, Integer billYear, Integer billMonth) {
        return monthlyBillRepository.findByContract_Room_IdAndBillYearAndBillMonth(roomId, billYear, billMonth)
                .stream().anyMatch(bill -> bill.getPaymentStatus() == PaymentStatus.DA_THANH_TOAN);
    }

    /** Most recent reading strictly before the given period — used both to auto-chain {@code oldReading} and for the grid preview. */
    private Optional<MeterReading> findPreviousReading(Long roomId, Long categoryId, Integer billYear, Integer billMonth) {
        return meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdOrderByBillYearDescBillMonthDesc(roomId, categoryId).stream()
                .filter(r -> isBefore(r.getBillYear(), r.getBillMonth(), billYear, billMonth))
                .findFirst();
    }

    private static boolean isBefore(int year, int month, int refYear, int refMonth) {
        return year < refYear || (year == refYear && month < refMonth);
    }
}
