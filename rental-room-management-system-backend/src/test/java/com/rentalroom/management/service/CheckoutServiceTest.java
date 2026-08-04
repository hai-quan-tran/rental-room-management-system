package com.rentalroom.management.service;

import com.rentalroom.management.dto.request.CheckoutItemRequest;
import com.rentalroom.management.dto.request.CheckoutRequest;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.CheckoutChecklist;
import com.rentalroom.management.entity.Contract;
import com.rentalroom.management.entity.Item;
import com.rentalroom.management.entity.Room;
import com.rentalroom.management.entity.RoomTypeHandoverItem;
import com.rentalroom.management.enums.ContractStatus;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.CheckoutChecklistRepository;
import com.rentalroom.management.repository.ContractRepository;
import com.rentalroom.management.repository.DebtRecordRepository;
import com.rentalroom.management.repository.RoomRepository;
import com.rentalroom.management.repository.RoomTypeHandoverItemRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutServiceTest {

    private ContractRepository contractRepository;
    private RoomRepository roomRepository;
    private RoomTypeHandoverItemRepository handoverItemRepository;
    private CheckoutChecklistRepository checkoutChecklistRepository;
    private DebtRecordRepository debtRecordRepository;
    private BillingService billingService;
    private BranchService branchService;
    private ItemService itemService;
    private CheckoutService checkoutService;

    private MockedStatic<SecurityUtils> securityUtils;

    private Contract contract;
    private RoomTypeHandoverItem lightBulbHandoverItem;

    @BeforeEach
    void setUp() {
        contractRepository = mock(ContractRepository.class);
        roomRepository = mock(RoomRepository.class);
        handoverItemRepository = mock(RoomTypeHandoverItemRepository.class);
        checkoutChecklistRepository = mock(CheckoutChecklistRepository.class);
        debtRecordRepository = mock(DebtRecordRepository.class);
        billingService = mock(BillingService.class);
        branchService = mock(BranchService.class);
        itemService = mock(ItemService.class);

        checkoutService = new CheckoutService(contractRepository, roomRepository, handoverItemRepository,
                checkoutChecklistRepository, debtRecordRepository, billingService, branchService, itemService);

        Branch branch = new Branch();
        branch.setId(1L);

        Room room = new Room();
        room.setId(50L);
        room.setBranch(branch);

        contract = new Contract();
        contract.setId(500L);
        contract.setRoom(room);
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setStartDate(LocalDate.of(2026, 1, 1));
        contract.setDepositAmount(new BigDecimal("5000000"));

        Item lightBulb = new Item();
        lightBulb.setId(900L);
        lightBulb.setBranch(branch);
        lightBulb.setName("Bóng đèn");
        lightBulb.setPrice(new BigDecimal("50000"));
        lightBulb.setQuantityAvailable(20);

        lightBulbHandoverItem = new RoomTypeHandoverItem();
        lightBulbHandoverItem.setId(901L);
        lightBulbHandoverItem.setItem(lightBulb);
        lightBulbHandoverItem.setQuantity(3);

        when(contractRepository.findById(500L)).thenReturn(Optional.of(contract));
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
        when(checkoutChecklistRepository.save(any(CheckoutChecklist.class))).thenAnswer(inv -> inv.getArgument(0));
        when(billingService.totalOutstandingBillDebt(500L)).thenReturn(BigDecimal.ZERO);

        securityUtils = Mockito.mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::currentUser)
                .thenReturn(new UserPrincipal(1L, "admin", Role.ADMIN_TONG, List.of()));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void damagedPlusLostExceedingTotalQuantity_throwsValidationError() {
        when(handoverItemRepository.findById(901L)).thenReturn(Optional.of(lightBulbHandoverItem));

        CheckoutRequest request = new CheckoutRequest(LocalDate.of(2026, 3, 10), null, List.of(
                new CheckoutItemRequest(901L, 2, 2, new BigDecimal("200000"), null)
        ));

        assertThrows(BusinessException.class, () -> checkoutService.checkout(500L, request));
        verify(itemService, never()).decrementStock(any(), anyInt());
    }

    @Test
    void lostQuantity_decrementsItemStock_damagedQuantity_doesNot() {
        when(handoverItemRepository.findById(901L)).thenReturn(Optional.of(lightBulbHandoverItem));

        CheckoutRequest request = new CheckoutRequest(LocalDate.of(2026, 3, 10), null, List.of(
                new CheckoutItemRequest(901L, 1, 1, new BigDecimal("100000"), null)
        ));

        assertDoesNotThrow(() -> checkoutService.checkout(500L, request));
        verify(itemService).decrementStock(eq(900L), eq(1));
    }

    @Test
    void allIntact_noDeductionOrStockChange() {
        when(handoverItemRepository.findById(901L)).thenReturn(Optional.of(lightBulbHandoverItem));

        CheckoutRequest request = new CheckoutRequest(LocalDate.of(2026, 3, 10), null, List.of(
                new CheckoutItemRequest(901L, 0, 0, BigDecimal.ZERO, null)
        ));

        assertDoesNotThrow(() -> checkoutService.checkout(500L, request));
        verify(itemService, never()).decrementStock(any(), anyInt());
    }
}
