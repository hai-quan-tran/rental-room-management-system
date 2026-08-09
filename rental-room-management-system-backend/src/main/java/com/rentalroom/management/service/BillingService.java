package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.common.util.MoneyUtils;
import com.rentalroom.management.dto.request.ExtraFeeItemRequest;
import com.rentalroom.management.dto.request.MonthlyBillCreateRequest;
import com.rentalroom.management.dto.request.PaymentRequest;
import com.rentalroom.management.dto.response.BulkMonthlyBillCreateResponse;
import com.rentalroom.management.dto.response.ExtraFeeItemResponse;
import com.rentalroom.management.dto.response.MonthlyBillDetailResponse;
import com.rentalroom.management.dto.response.MonthlyBillListResponse;
import com.rentalroom.management.dto.response.MonthlyBillResponse;
import com.rentalroom.management.dto.response.PaymentResponse;
import com.rentalroom.management.common.util.MoneyUtils;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Contract;
import com.rentalroom.management.entity.ExtraFeeCategory;
import com.rentalroom.management.entity.ExtraFeeItem;
import com.rentalroom.management.entity.MeterReading;
import com.rentalroom.management.entity.MonthlyBill;
import com.rentalroom.management.entity.Payment;
import com.rentalroom.management.entity.UtilityRate;
import com.rentalroom.management.enums.BillSyncStatus;
import com.rentalroom.management.enums.ContractStatus;
import com.rentalroom.management.enums.PaymentStatus;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.ContractRepository;
import com.rentalroom.management.repository.ExtraFeeCategoryRepository;
import com.rentalroom.management.repository.ExtraFeeItemRepository;
import com.rentalroom.management.repository.MeterReadingRepository;
import com.rentalroom.management.repository.MonthlyBillRepository;
import com.rentalroom.management.repository.PaymentRepository;
import com.rentalroom.management.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BillingService {

    private final MonthlyBillRepository monthlyBillRepository;
    private final ExtraFeeItemRepository extraFeeItemRepository;
    private final ExtraFeeCategoryRepository extraFeeCategoryRepository;
    private final PaymentRepository paymentRepository;
    private final ContractRepository contractRepository;
    private final BranchRepository branchRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final UtilityRateService utilityRateService;
    private final EntityManager entityManager;

    public BillingService(MonthlyBillRepository monthlyBillRepository,
                           ExtraFeeItemRepository extraFeeItemRepository,
                           ExtraFeeCategoryRepository extraFeeCategoryRepository,
                           PaymentRepository paymentRepository,
                           ContractRepository contractRepository,
                           BranchRepository branchRepository,
                           MeterReadingRepository meterReadingRepository,
                           UtilityRateService utilityRateService,
                           EntityManager entityManager) {
        this.monthlyBillRepository = monthlyBillRepository;
        this.extraFeeItemRepository = extraFeeItemRepository;
        this.extraFeeCategoryRepository = extraFeeCategoryRepository;
        this.paymentRepository = paymentRepository;
        this.contractRepository = contractRepository;
        this.branchRepository = branchRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.utilityRateService = utilityRateService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<MonthlyBillResponse> listByContract(Long contractId) {
        Contract contract = findContract(contractId);
        SecurityUtils.assertCanAccessBranch(contract.getRoom().getBranch().getId());
        return monthlyBillRepository.findByContractIdOrderByBillYearDescBillMonthDesc(contractId).stream()
                .map(MonthlyBillResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonthlyBillDetailResponse get(Long billId) {
        return toDetail(findBillWithAccessCheck(billId));
    }

    /**
     * Cross-room bill listing for the "Hóa đơn hằng tháng của các phòng" screen. Branch scoping
     * mirrors {@link DashboardService#getDashboard} — ADMIN_CAP_1 always gets its own managed
     * branches from the JWT (the client-supplied {@code branchId} is ignored for that role).
     */
    @Transactional(readOnly = true)
    public PageResponse<MonthlyBillListResponse> listAll(Integer billYear, Integer billMonth, Long branchId, Pageable pageable) {
        List<Long> branchIds = SecurityUtils.resolveBranchScope(
                branchId, () -> branchRepository.findAll().stream().map(Branch::getId).toList());

        if (branchIds.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }

        Page<MonthlyBill> page = monthlyBillRepository
                .findByBillYearAndBillMonthAndContract_Room_BranchIdIn(billYear, billMonth, branchIds, pageable);
        return PageResponse.from(page.map(MonthlyBillListResponse::from));
    }

    /**
     * Bulk-creates the rent-only bill "shell" for every active contract in scope that doesn't
     * already have a bill for the given month — used by the "Tạo hóa đơn hàng loạt" action on the
     * "Hóa đơn hằng tháng của các phòng" screen so an admin doesn't have to open each room
     * individually. Extra fee items (điện/nước/...) still need to be added per-bill afterward,
     * since those readings differ per room. Branch scoping mirrors {@link #listAll}.
     */
    public BulkMonthlyBillCreateResponse bulkCreate(Integer billYear, Integer billMonth, Long branchId) {
        List<Long> branchIds = SecurityUtils.resolveBranchScope(
                branchId, () -> branchRepository.findAll().stream().map(Branch::getId).toList());

        if (branchIds.isEmpty()) {
            return new BulkMonthlyBillCreateResponse(List.of(), 0, 0);
        }

        List<MonthlyBillListResponse> created = new ArrayList<>();
        int alreadyExists = 0;
        int notApplicable = 0;
        for (Contract contract : contractRepository.findByStatusAndRoom_BranchIdIn(ContractStatus.ACTIVE, branchIds)) {
            if (monthlyBillRepository.existsByContractIdAndBillYearAndBillMonth(contract.getId(), billYear, billMonth)) {
                alreadyExists++;
                continue;
            }

            BigDecimal rentAmount = RentCalculator.calculateProratedRent(
                    contract.getMonthlyRent(), billYear, billMonth, contract.getStartDate(), contract.getEndDate());
            if (rentAmount.signum() == 0) {
                notApplicable++;
                continue;
            }

            MonthlyBill bill = new MonthlyBill();
            bill.setContract(contract);
            bill.setBillMonth(billMonth);
            bill.setBillYear(billYear);
            bill.setRentAmount(rentAmount);
            bill.setTotalExtraFee(BigDecimal.ZERO);
            bill.setWifiFee(contract.getRoom().getWifiFee());
            bill.setParkingFee(contract.getRoom().getParkingFee());
            bill.setPaidAmount(BigDecimal.ZERO);
            MonthlyBill saved = monthlyBillRepository.save(bill);
            entityManager.flush();
            entityManager.refresh(saved);
            seedMeteredExtraFeeItems(saved);
            recalculateExtraFeeTotal(saved);
            created.add(MonthlyBillListResponse.from(saved));
        }
        return new BulkMonthlyBillCreateResponse(created, alreadyExists, notApplicable);
    }

    public MonthlyBillResponse createBill(Long contractId, MonthlyBillCreateRequest request) {
        Contract contract = findContract(contractId);
        SecurityUtils.assertCanAccessBranch(contract.getRoom().getBranch().getId());

        if (monthlyBillRepository.existsByContractIdAndBillYearAndBillMonth(contractId, request.billYear(), request.billMonth())) {
            throw BusinessException.conflict("Hóa đơn tháng này đã tồn tại cho hợp đồng");
        }

        BigDecimal rentAmount = RentCalculator.calculateProratedRent(
                contract.getMonthlyRent(), request.billYear(), request.billMonth(),
                contract.getStartDate(), contract.getEndDate());

        MonthlyBill bill = new MonthlyBill();
        bill.setContract(contract);
        bill.setBillMonth(request.billMonth());
        bill.setBillYear(request.billYear());
        bill.setRentAmount(rentAmount);
        bill.setTotalExtraFee(BigDecimal.ZERO);
        bill.setWifiFee(contract.getRoom().getWifiFee());
        bill.setParkingFee(contract.getRoom().getParkingFee());
        bill.setPaidAmount(BigDecimal.ZERO);
        MonthlyBill saved = monthlyBillRepository.save(bill);
        entityManager.flush();
        entityManager.refresh(saved);
        seedMeteredExtraFeeItems(saved);
        recalculateExtraFeeTotal(saved);
        return MonthlyBillResponse.from(saved);
    }

    public ExtraFeeItemResponse addExtraFeeItem(Long billId, ExtraFeeItemRequest request) {
        MonthlyBill bill = findBillWithAccessCheck(billId);
        assertNotFullyPaid(bill);
        ExtraFeeCategory category = extraFeeCategoryRepository.findById(request.extraFeeCategoryId())
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy danh mục chi phí"));

        ExtraFeeItem item = new ExtraFeeItem();
        item.setMonthlyBill(bill);
        item.setExtraFeeCategory(category);
        item.setAmount(request.amount());
        item.setNote(request.note());
        ExtraFeeItem saved = extraFeeItemRepository.save(item);

        recalculateExtraFeeTotal(bill);
        return ExtraFeeItemResponse.from(saved);
    }

    public void deleteExtraFeeItem(Long billId, Long itemId) {
        MonthlyBill bill = findBillWithAccessCheck(billId);
        assertNotFullyPaid(bill);
        ExtraFeeItem item = extraFeeItemRepository.findByIdAndMonthlyBillId(itemId, billId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy khoản chi phí"));
        extraFeeItemRepository.delete(item);
        recalculateExtraFeeTotal(bill);
    }

    public MonthlyBillResponse recordPayment(Long billId, PaymentRequest request) {
        MonthlyBill bill = findBillWithAccessCheck(billId);
        if (request.amount().compareTo(bill.getRemainingAmount()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số tiền thanh toán vượt quá số tiền còn nợ");
        }

        Payment payment = new Payment();
        payment.setMonthlyBill(bill);
        payment.setAmount(request.amount());
        payment.setPaymentDate(request.paymentDate());
        payment.setMethod(request.method());
        payment.setNote(request.note());
        payment.setCreatedBy(SecurityUtils.currentUser().userId());
        paymentRepository.save(payment);

        // paid_amount/payment_status are updated by trg_payment_after_insert — reload to see it.
        entityManager.flush();
        entityManager.refresh(bill);
        return MonthlyBillResponse.from(bill);
    }

    /**
     * Called by {@link CheckoutService} once the contract's final {@code endDate} is known —
     * finds or creates the checkout month's bill and (re)computes its prorated rent, since a
     * bill generated earlier in the month (while the contract was still open-ended) may have
     * assumed a different occupancy length.
     */
    MonthlyBill upsertRentForCheckoutMonth(Contract contract) {
        YearMonth yearMonth = YearMonth.from(contract.getEndDate());
        BigDecimal rentAmount = RentCalculator.calculateProratedRent(
                contract.getMonthlyRent(), yearMonth.getYear(), yearMonth.getMonthValue(),
                contract.getStartDate(), contract.getEndDate());

        MonthlyBill bill = monthlyBillRepository
                .findByContractIdAndBillYearAndBillMonth(contract.getId(), yearMonth.getYear(), yearMonth.getMonthValue())
                .orElseGet(() -> {
                    MonthlyBill created = new MonthlyBill();
                    created.setContract(contract);
                    created.setBillYear(yearMonth.getYear());
                    created.setBillMonth(yearMonth.getMonthValue());
                    created.setTotalExtraFee(BigDecimal.ZERO);
                    created.setWifiFee(contract.getRoom().getWifiFee());
                    created.setParkingFee(contract.getRoom().getParkingFee());
                    created.setPaidAmount(BigDecimal.ZERO);
                    return created;
                });
        bill.setRentAmount(rentAmount);
        MonthlyBill saved = monthlyBillRepository.save(bill);
        entityManager.flush();
        entityManager.refresh(saved);
        return saved;
    }

    /** Sum of {@code remainingAmount} across every bill of this contract that still has a balance. */
    BigDecimal totalOutstandingBillDebt(Long contractId) {
        return monthlyBillRepository.findByContractIdOrderByBillYearDescBillMonthDesc(contractId).stream()
                .map(MonthlyBill::getRemainingAmount)
                .filter(remaining -> remaining.signum() > 0)
                .collect(MoneyUtils.summing());
    }

    /**
     * Creates 1 {@link ExtraFeeItem} for every metered category (Điện, Nước) on a freshly-created
     * bill, so the admin never has to remember to add them manually. Amount is computed from
     * whatever {@link MeterReading}/{@link UtilityRate} already exist for that room+month — 0 with
     * an explanatory note when either is missing (never throws, so 1 branch missing a rate can't
     * break bill creation for every room).
     */
    private void seedMeteredExtraFeeItems(MonthlyBill bill) {
        Long branchId = bill.getContract().getRoom().getBranch().getId();
        Long roomId = bill.getContract().getRoom().getId();
        LocalDate referenceDate = LocalDate.of(bill.getBillYear(), bill.getBillMonth(), 1);

        for (ExtraFeeCategory category : extraFeeCategoryRepository.findByMeteredTrue()) {
            BigDecimal amount = BigDecimal.ZERO;
            String note = "Chưa có chỉ số";

            Optional<MeterReading> reading = meterReadingRepository
                    .findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(
                            roomId, category.getId(), bill.getBillYear(), bill.getBillMonth());
            if (reading.isPresent()) {
                Optional<UtilityRate> rate = utilityRateService.findCurrentRate(branchId, category.getId(), referenceDate);
                if (rate.isPresent()) {
                    amount = MoneyUtils.roundToWholeVnd(reading.get().getConsumption().multiply(rate.get().getUnitPrice()));
                    note = "Tự động: chỉ số " + reading.get().getOldReading() + "→" + reading.get().getNewReading()
                            + " × " + rate.get().getUnitPrice() + "đ";
                } else {
                    note = "Đã có chỉ số nhưng chưa cấu hình đơn giá";
                }
            }

            ExtraFeeItem item = new ExtraFeeItem();
            item.setMonthlyBill(bill);
            item.setExtraFeeCategory(category);
            item.setAmount(amount);
            item.setNote(note);
            extraFeeItemRepository.save(item);
        }
    }

    /**
     * Called by {@link MeterReadingService} after a meter reading is saved/updated — if exactly 1
     * bill exists for that room+month, updates that bill's extra-fee item for the given category in
     * place (creating it if somehow missing, never adding a duplicate) and recalculates the total.
     * Never throws: skips silently-but-reported (via the returned status) when the bill is already
     * fully paid, or when >1 bill matches (contract changed mid-month, ambiguous which bill owns the
     * reading) — the reading itself is always saved by the caller regardless of this result.
     */
    BillSyncStatus syncMeteredExtraFeeItem(Long roomId, Integer billYear, Integer billMonth,
                                            ExtraFeeCategory category, BigDecimal amount, String note) {
        List<MonthlyBill> bills = monthlyBillRepository.findByContract_Room_IdAndBillYearAndBillMonth(roomId, billYear, billMonth);
        if (bills.isEmpty()) {
            return BillSyncStatus.NO_BILL_YET;
        }
        if (bills.size() > 1) {
            return BillSyncStatus.SKIPPED_AMBIGUOUS_CONTRACT;
        }

        MonthlyBill bill = bills.get(0);
        if (bill.getPaymentStatus() == PaymentStatus.DA_THANH_TOAN) {
            return BillSyncStatus.SKIPPED_PAID;
        }

        ExtraFeeItem item = extraFeeItemRepository.findByMonthlyBillIdOrderByIdAsc(bill.getId()).stream()
                .filter(existing -> existing.getExtraFeeCategory().getId().equals(category.getId()))
                .findFirst()
                .orElseGet(() -> {
                    ExtraFeeItem created = new ExtraFeeItem();
                    created.setMonthlyBill(bill);
                    created.setExtraFeeCategory(category);
                    return created;
                });
        item.setAmount(amount);
        item.setNote(note);
        extraFeeItemRepository.save(item);
        recalculateExtraFeeTotal(bill);
        return BillSyncStatus.UPDATED;
    }

    private void recalculateExtraFeeTotal(MonthlyBill bill) {
        BigDecimal total = extraFeeItemRepository.findByMonthlyBillIdOrderByIdAsc(bill.getId()).stream()
                .map(ExtraFeeItem::getAmount)
                .collect(MoneyUtils.summing());
        bill.setTotalExtraFee(total);
        monthlyBillRepository.save(bill);
        entityManager.flush();
        entityManager.refresh(bill);
    }

    private MonthlyBillDetailResponse toDetail(MonthlyBill bill) {
        List<ExtraFeeItemResponse> items = extraFeeItemRepository.findByMonthlyBillIdOrderByIdAsc(bill.getId())
                .stream().map(ExtraFeeItemResponse::from).toList();
        List<PaymentResponse> payments = paymentRepository.findByMonthlyBillIdOrderByPaymentDateDescIdDesc(bill.getId())
                .stream().map(PaymentResponse::from).toList();
        return new MonthlyBillDetailResponse(MonthlyBillResponse.from(bill), items, payments);
    }

    private void assertNotFullyPaid(MonthlyBill bill) {
        if (bill.getPaymentStatus() == PaymentStatus.DA_THANH_TOAN) {
            throw BusinessException.conflict("Hóa đơn đã thanh toán đủ, không thể sửa chi phí phát sinh");
        }
    }

    private MonthlyBill findBillWithAccessCheck(Long billId) {
        MonthlyBill bill = monthlyBillRepository.findById(billId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy hóa đơn"));
        SecurityUtils.assertCanAccessBranch(bill.getContract().getRoom().getBranch().getId());
        return bill;
    }

    private Contract findContract(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy hợp đồng"));
    }
}
