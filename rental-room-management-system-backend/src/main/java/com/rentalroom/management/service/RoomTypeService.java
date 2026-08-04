package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.config.RedisConfig;
import com.rentalroom.management.dto.request.HandoverItemRequest;
import com.rentalroom.management.dto.request.RoomTypeRequest;
import com.rentalroom.management.dto.response.HandoverItemResponse;
import com.rentalroom.management.dto.response.RoomTypeResponse;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Item;
import com.rentalroom.management.entity.RoomType;
import com.rentalroom.management.entity.RoomTypeHandoverItem;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.ItemRepository;
import com.rentalroom.management.repository.RoomRepository;
import com.rentalroom.management.repository.RoomTypeHandoverItemRepository;
import com.rentalroom.management.repository.RoomTypeRepository;
import com.rentalroom.management.security.SecurityUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomTypeHandoverItemRepository handoverItemRepository;
    private final RoomRepository roomRepository;
    private final BranchRepository branchRepository;
    private final ItemRepository itemRepository;

    public RoomTypeService(RoomTypeRepository roomTypeRepository,
                            RoomTypeHandoverItemRepository handoverItemRepository,
                            RoomRepository roomRepository,
                            BranchRepository branchRepository,
                            ItemRepository itemRepository) {
        this.roomTypeRepository = roomTypeRepository;
        this.handoverItemRepository = handoverItemRepository;
        this.roomRepository = roomRepository;
        this.branchRepository = branchRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<RoomTypeResponse> list(Long branchId, String search, Pageable pageable) {
        SecurityUtils.assertCanAccessBranch(branchId);
        var page = (search == null || search.isBlank())
                ? roomTypeRepository.findByBranchId(branchId, pageable)
                : roomTypeRepository.findByBranchIdAndNameContainingIgnoreCase(branchId, search, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RedisConfig.CACHE_ROOM_TYPES, key = "#branchId")
    public List<RoomTypeResponse> listAll(Long branchId) {
        SecurityUtils.assertCanAccessBranch(branchId);
        return roomTypeRepository.findByBranchIdOrderByNameAsc(branchId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoomTypeResponse get(Long id) {
        RoomType roomType = findEntity(id);
        SecurityUtils.assertCanAccessBranch(roomType.getBranch().getId());
        return toResponse(roomType);
    }

    @CacheEvict(cacheNames = RedisConfig.CACHE_ROOM_TYPES, allEntries = true)
    public RoomTypeResponse create(Long branchId, RoomTypeRequest request) {
        SecurityUtils.assertCanAccessBranch(branchId);
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy chi nhánh"));
        if (roomTypeRepository.existsByBranchIdAndName(branchId, request.name())) {
            throw BusinessException.conflict("Tên loại phòng đã tồn tại trong chi nhánh này");
        }
        RoomType roomType = new RoomType();
        roomType.setBranch(branch);
        roomType.setName(request.name());
        roomType.setArea(request.area());
        roomType.setDescription(request.description());
        return toResponse(roomTypeRepository.save(roomType));
    }

    @CacheEvict(cacheNames = RedisConfig.CACHE_ROOM_TYPES, allEntries = true)
    public RoomTypeResponse update(Long id, RoomTypeRequest request) {
        RoomType roomType = findEntity(id);
        SecurityUtils.assertCanAccessBranch(roomType.getBranch().getId());
        if (!roomType.getName().equals(request.name())
                && roomTypeRepository.existsByBranchIdAndNameAndIdNot(roomType.getBranch().getId(), request.name(), id)) {
            throw BusinessException.conflict("Tên loại phòng đã tồn tại trong chi nhánh này");
        }
        roomType.setName(request.name());
        roomType.setArea(request.area());
        roomType.setDescription(request.description());
        return toResponse(roomTypeRepository.save(roomType));
    }

    @CacheEvict(cacheNames = RedisConfig.CACHE_ROOM_TYPES, allEntries = true)
    public void delete(Long id) {
        RoomType roomType = findEntity(id);
        SecurityUtils.assertCanAccessBranch(roomType.getBranch().getId());
        if (roomRepository.existsByRoomTypeId(id)) {
            throw BusinessException.conflict("Không thể xóa loại phòng đang được sử dụng bởi phòng trọ");
        }
        roomTypeRepository.deleteById(id);
    }

    /** Replaces the whole handover-item template for this room type (spec: edited only here, not per-room). */
    @CacheEvict(cacheNames = RedisConfig.CACHE_ROOM_TYPES, allEntries = true)
    public List<HandoverItemResponse> replaceHandoverItems(Long roomTypeId, List<HandoverItemRequest> items) {
        RoomType roomType = findEntity(roomTypeId);
        SecurityUtils.assertCanAccessBranch(roomType.getBranch().getId());

        Set<Long> seenItemIds = new HashSet<>();
        List<RoomTypeHandoverItem> entities = items.stream().map(request -> {
            if (!seenItemIds.add(request.itemId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Một vật dụng chỉ được chọn 1 lần trong danh sách");
            }
            Item item = itemRepository.findById(request.itemId())
                    .orElseThrow(() -> BusinessException.notFound("Không tìm thấy vật dụng"));
            if (!item.getBranch().getId().equals(roomType.getBranch().getId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Vật dụng \"" + item.getName() + "\" không thuộc chi nhánh của loại phòng này");
            }
            if (request.quantity() > item.getQuantityAvailable()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Số lượng \"" + item.getName() + "\" vượt quá tổng số lượng đang có tại chi nhánh ("
                                + item.getQuantityAvailable() + ")");
            }
            RoomTypeHandoverItem handoverItem = new RoomTypeHandoverItem();
            handoverItem.setRoomType(roomType);
            handoverItem.setItem(item);
            handoverItem.setQuantity(request.quantity());
            handoverItem.setNote(request.note());
            return handoverItem;
        }).toList();

        handoverItemRepository.deleteByRoomTypeId(roomTypeId);
        handoverItemRepository.flush();
        return handoverItemRepository.saveAll(entities).stream().map(HandoverItemResponse::from).toList();
    }

    private RoomTypeResponse toResponse(RoomType roomType) {
        List<HandoverItemResponse> items = handoverItemRepository.findByRoomTypeIdOrderByIdAsc(roomType.getId())
                .stream().map(HandoverItemResponse::from).toList();
        return RoomTypeResponse.from(roomType, items);
    }

    private RoomType findEntity(Long id) {
        return roomTypeRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy loại phòng"));
    }
}
