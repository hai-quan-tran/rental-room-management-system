package com.rentalroom.management.service;

import com.rentalroom.management.dto.request.ExtraFeeCategoryRequest;
import com.rentalroom.management.dto.response.ExtraFeeCategoryResponse;
import com.rentalroom.management.entity.ExtraFeeCategory;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.ExtraFeeCategoryRepository;
import com.rentalroom.management.repository.ExtraFeeItemRepository;
import com.rentalroom.management.repository.MeterReadingRepository;
import com.rentalroom.management.repository.UtilityRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExtraFeeCategoryServiceTest {

    private ExtraFeeCategoryRepository extraFeeCategoryRepository;
    private ExtraFeeItemRepository extraFeeItemRepository;
    private UtilityRateRepository utilityRateRepository;
    private MeterReadingRepository meterReadingRepository;
    private ExtraFeeCategoryService extraFeeCategoryService;

    private ExtraFeeCategory electricity;
    private ExtraFeeCategory wifi;

    @BeforeEach
    void setUp() {
        extraFeeCategoryRepository = mock(ExtraFeeCategoryRepository.class);
        extraFeeItemRepository = mock(ExtraFeeItemRepository.class);
        utilityRateRepository = mock(UtilityRateRepository.class);
        meterReadingRepository = mock(MeterReadingRepository.class);

        extraFeeCategoryService = new ExtraFeeCategoryService(extraFeeCategoryRepository, extraFeeItemRepository,
                utilityRateRepository, meterReadingRepository);

        electricity = new ExtraFeeCategory();
        electricity.setId(10L);
        electricity.setName("Điện");
        electricity.setUnit("kWh");
        electricity.setMetered(true);

        wifi = new ExtraFeeCategory();
        wifi.setId(20L);
        wifi.setName("Wifi");
        wifi.setUnit(null);
        wifi.setMetered(false);

        when(extraFeeCategoryRepository.findById(10L)).thenReturn(Optional.of(electricity));
        when(extraFeeCategoryRepository.findById(20L)).thenReturn(Optional.of(wifi));
        when(extraFeeCategoryRepository.save(any(ExtraFeeCategory.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void listAll_returnsOrderedResponses() {
        when(extraFeeCategoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(electricity, wifi));

        List<ExtraFeeCategoryResponse> responses = extraFeeCategoryService.listAll();

        assertEquals(2, responses.size());
        assertEquals("Điện", responses.get(0).name());
    }

    @Test
    void create_duplicateName_throwsConflict() {
        when(extraFeeCategoryRepository.existsByName("Điện")).thenReturn(true);
        assertThrows(BusinessException.class,
                () -> extraFeeCategoryService.create(new ExtraFeeCategoryRequest("Điện", "kWh")));
    }

    @Test
    void create_valid_savesCategory() {
        when(extraFeeCategoryRepository.existsByName("Rác")).thenReturn(false);

        ExtraFeeCategoryResponse response = assertDoesNotThrow(
                () -> extraFeeCategoryService.create(new ExtraFeeCategoryRequest("Rác", "tháng")));

        assertEquals("Rác", response.name());
        assertEquals("tháng", response.unit());
    }

    @Test
    void update_notFound_throwsNotFound() {
        when(extraFeeCategoryRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class,
                () -> extraFeeCategoryService.update(999L, new ExtraFeeCategoryRequest("Rác", "tháng")));
    }

    @Test
    void update_renamedToExistingName_throwsConflict() {
        when(extraFeeCategoryRepository.existsByNameAndIdNot("Wifi", 10L)).thenReturn(true);
        assertThrows(BusinessException.class,
                () -> extraFeeCategoryService.update(10L, new ExtraFeeCategoryRequest("Wifi", "kWh")));
    }

    @Test
    void update_sameName_skipsDuplicateCheck() {
        assertDoesNotThrow(() -> extraFeeCategoryService.update(10L, new ExtraFeeCategoryRequest("Điện", "kWh mới")));
        verify(extraFeeCategoryRepository, never()).existsByNameAndIdNot(any(), any());
    }

    @Test
    void update_valid_updatesFields() {
        when(extraFeeCategoryRepository.existsByNameAndIdNot("Điện sinh hoạt", 10L)).thenReturn(false);

        ExtraFeeCategoryResponse response = assertDoesNotThrow(
                () -> extraFeeCategoryService.update(10L, new ExtraFeeCategoryRequest("Điện sinh hoạt", "kWh")));

        assertEquals("Điện sinh hoạt", response.name());
        assertEquals("Điện sinh hoạt", electricity.getName());
    }

    @Test
    void delete_notFound_throwsNotFound() {
        when(extraFeeCategoryRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> extraFeeCategoryService.delete(999L));
    }

    @Test
    void delete_usedInBill_throwsConflict() {
        when(extraFeeItemRepository.existsByExtraFeeCategoryId(10L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> extraFeeCategoryService.delete(10L));
        verify(extraFeeCategoryRepository, never()).deleteById(any());
    }

    @Test
    void delete_meteredWithExistingRate_throwsConflict() {
        when(extraFeeItemRepository.existsByExtraFeeCategoryId(10L)).thenReturn(false);
        when(utilityRateRepository.existsByExtraFeeCategoryId(10L)).thenReturn(true);

        assertThrows(BusinessException.class, () -> extraFeeCategoryService.delete(10L));
        verify(extraFeeCategoryRepository, never()).deleteById(any());
    }

    @Test
    void delete_meteredWithExistingMeterReading_throwsConflict() {
        when(extraFeeItemRepository.existsByExtraFeeCategoryId(10L)).thenReturn(false);
        when(utilityRateRepository.existsByExtraFeeCategoryId(10L)).thenReturn(false);
        when(meterReadingRepository.existsByExtraFeeCategoryId(10L)).thenReturn(true);

        assertThrows(BusinessException.class, () -> extraFeeCategoryService.delete(10L));
        verify(extraFeeCategoryRepository, never()).deleteById(any());
    }

    @Test
    void delete_notMetered_skipsRateAndReadingChecks() {
        when(extraFeeItemRepository.existsByExtraFeeCategoryId(20L)).thenReturn(false);

        assertDoesNotThrow(() -> extraFeeCategoryService.delete(20L));

        verify(utilityRateRepository, never()).existsByExtraFeeCategoryId(any());
        verify(meterReadingRepository, never()).existsByExtraFeeCategoryId(any());
        verify(extraFeeCategoryRepository).deleteById(20L);
    }

    @Test
    void delete_valid_deletesCategory() {
        when(extraFeeItemRepository.existsByExtraFeeCategoryId(10L)).thenReturn(false);
        when(utilityRateRepository.existsByExtraFeeCategoryId(10L)).thenReturn(false);
        when(meterReadingRepository.existsByExtraFeeCategoryId(10L)).thenReturn(false);

        assertDoesNotThrow(() -> extraFeeCategoryService.delete(10L));

        verify(extraFeeCategoryRepository).deleteById(10L);
    }
}
