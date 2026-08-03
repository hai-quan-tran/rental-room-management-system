package com.rentalroom.management.service;

import com.rentalroom.management.dto.request.ExtraFeeCategoryRequest;
import com.rentalroom.management.dto.response.ExtraFeeCategoryResponse;
import com.rentalroom.management.entity.ExtraFeeCategory;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.ExtraFeeCategoryRepository;
import com.rentalroom.management.repository.ExtraFeeItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ExtraFeeCategoryService {

    private final ExtraFeeCategoryRepository extraFeeCategoryRepository;
    private final ExtraFeeItemRepository extraFeeItemRepository;

    public ExtraFeeCategoryService(ExtraFeeCategoryRepository extraFeeCategoryRepository,
                                    ExtraFeeItemRepository extraFeeItemRepository) {
        this.extraFeeCategoryRepository = extraFeeCategoryRepository;
        this.extraFeeItemRepository = extraFeeItemRepository;
    }

    @Transactional(readOnly = true)
    public List<ExtraFeeCategoryResponse> listAll() {
        return extraFeeCategoryRepository.findAllByOrderByNameAsc().stream()
                .map(ExtraFeeCategoryResponse::from)
                .toList();
    }

    public ExtraFeeCategoryResponse create(ExtraFeeCategoryRequest request) {
        if (extraFeeCategoryRepository.existsByName(request.name())) {
            throw BusinessException.conflict("Tên danh mục đã tồn tại");
        }
        ExtraFeeCategory category = new ExtraFeeCategory();
        category.setName(request.name());
        category.setUnit(request.unit());
        return ExtraFeeCategoryResponse.from(extraFeeCategoryRepository.save(category));
    }

    public ExtraFeeCategoryResponse update(Long id, ExtraFeeCategoryRequest request) {
        ExtraFeeCategory category = findEntity(id);
        if (!category.getName().equals(request.name())
                && extraFeeCategoryRepository.existsByNameAndIdNot(request.name(), id)) {
            throw BusinessException.conflict("Tên danh mục đã tồn tại");
        }
        category.setName(request.name());
        category.setUnit(request.unit());
        return ExtraFeeCategoryResponse.from(extraFeeCategoryRepository.save(category));
    }

    public void delete(Long id) {
        findEntity(id);
        if (extraFeeItemRepository.existsByExtraFeeCategoryId(id)) {
            throw BusinessException.conflict("Không thể xóa danh mục đã được sử dụng trong hóa đơn");
        }
        extraFeeCategoryRepository.deleteById(id);
    }

    private ExtraFeeCategory findEntity(Long id) {
        return extraFeeCategoryRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy danh mục chi phí"));
    }
}
