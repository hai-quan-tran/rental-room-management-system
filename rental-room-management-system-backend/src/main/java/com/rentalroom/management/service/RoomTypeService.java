package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.config.RedisConfig;
import com.rentalroom.management.dto.request.HandoverItemRequest;
import com.rentalroom.management.dto.request.RoomTypeRequest;
import com.rentalroom.management.dto.response.HandoverItemResponse;
import com.rentalroom.management.dto.response.RoomTypeResponse;
import com.rentalroom.management.entity.RoomType;
import com.rentalroom.management.entity.RoomTypeHandoverItem;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.RoomRepository;
import com.rentalroom.management.repository.RoomTypeHandoverItemRepository;
import com.rentalroom.management.repository.RoomTypeRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomTypeHandoverItemRepository handoverItemRepository;
    private final RoomRepository roomRepository;

    public RoomTypeService(RoomTypeRepository roomTypeRepository,
                            RoomTypeHandoverItemRepository handoverItemRepository,
                            RoomRepository roomRepository) {
        this.roomTypeRepository = roomTypeRepository;
        this.handoverItemRepository = handoverItemRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<RoomTypeResponse> list(String search, Pageable pageable) {
        var page = (search == null || search.isBlank())
                ? roomTypeRepository.findAll(pageable)
                : roomTypeRepository.findByNameContainingIgnoreCase(search, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RedisConfig.CACHE_ROOM_TYPES, key = "'all'")
    public List<RoomTypeResponse> listAll() {
        return roomTypeRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoomTypeResponse get(Long id) {
        return toResponse(findEntity(id));
    }

    @CacheEvict(cacheNames = RedisConfig.CACHE_ROOM_TYPES, key = "'all'")
    public RoomTypeResponse create(RoomTypeRequest request) {
        if (roomTypeRepository.existsByName(request.name())) {
            throw BusinessException.conflict("Tên loại phòng đã tồn tại");
        }
        RoomType roomType = new RoomType();
        roomType.setName(request.name());
        roomType.setArea(request.area());
        roomType.setDescription(request.description());
        return toResponse(roomTypeRepository.save(roomType));
    }

    @CacheEvict(cacheNames = RedisConfig.CACHE_ROOM_TYPES, key = "'all'")
    public RoomTypeResponse update(Long id, RoomTypeRequest request) {
        RoomType roomType = findEntity(id);
        if (!roomType.getName().equals(request.name()) && roomTypeRepository.existsByNameAndIdNot(request.name(), id)) {
            throw BusinessException.conflict("Tên loại phòng đã tồn tại");
        }
        roomType.setName(request.name());
        roomType.setArea(request.area());
        roomType.setDescription(request.description());
        return toResponse(roomTypeRepository.save(roomType));
    }

    @CacheEvict(cacheNames = RedisConfig.CACHE_ROOM_TYPES, key = "'all'")
    public void delete(Long id) {
        findEntity(id);
        if (roomRepository.existsByRoomTypeId(id)) {
            throw BusinessException.conflict("Không thể xóa loại phòng đang được sử dụng bởi phòng trọ");
        }
        roomTypeRepository.deleteById(id);
    }

    /** Replaces the whole handover-item template for this room type (spec: edited only here, not per-room). */
    @CacheEvict(cacheNames = RedisConfig.CACHE_ROOM_TYPES, key = "'all'")
    public List<HandoverItemResponse> replaceHandoverItems(Long roomTypeId, List<HandoverItemRequest> items) {
        RoomType roomType = findEntity(roomTypeId);
        handoverItemRepository.deleteByRoomTypeId(roomTypeId);
        List<RoomTypeHandoverItem> entities = items.stream().map(request -> {
            RoomTypeHandoverItem item = new RoomTypeHandoverItem();
            item.setRoomType(roomType);
            item.setItemName(request.itemName());
            item.setQuantity(request.quantity());
            item.setNote(request.note());
            return item;
        }).toList();
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
