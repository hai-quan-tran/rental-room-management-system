package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.ItemRequest;
import com.rentalroom.management.dto.response.ItemOptionResponse;
import com.rentalroom.management.dto.response.ItemResponse;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Item;
import com.rentalroom.management.entity.Room;
import com.rentalroom.management.entity.RoomTypeHandoverItem;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.ItemRepository;
import com.rentalroom.management.repository.RoomRepository;
import com.rentalroom.management.repository.RoomTypeHandoverItemRepository;
import com.rentalroom.management.security.SecurityUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class ItemService {

    private final ItemRepository itemRepository;
    private final BranchRepository branchRepository;
    private final RoomRepository roomRepository;
    private final RoomTypeHandoverItemRepository handoverItemRepository;

    public ItemService(ItemRepository itemRepository,
                        BranchRepository branchRepository,
                        RoomRepository roomRepository,
                        RoomTypeHandoverItemRepository handoverItemRepository) {
        this.itemRepository = itemRepository;
        this.branchRepository = branchRepository;
        this.roomRepository = roomRepository;
        this.handoverItemRepository = handoverItemRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> list(Long branchId, String search, Pageable pageable) {
        SecurityUtils.assertCanAccessBranch(branchId);
        var page = (search == null || search.isBlank())
                ? itemRepository.findByBranchId(branchId, pageable)
                : itemRepository.findByBranchIdAndNameContainingIgnoreCase(branchId, search, pageable);
        return PageResponse.from(page.map(ItemResponse::from));
    }

    @Transactional(readOnly = true)
    public List<ItemOptionResponse> listAll(Long branchId) {
        SecurityUtils.assertCanAccessBranch(branchId);
        return itemRepository.findByBranchIdOrderByNameAsc(branchId).stream().map(ItemOptionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ItemResponse get(Long id) {
        Item item = findEntity(id);
        SecurityUtils.assertCanAccessBranch(item.getBranch().getId());
        return ItemResponse.from(item);
    }

    public ItemResponse create(Long branchId, ItemRequest request) {
        SecurityUtils.assertCanAccessBranch(branchId);
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy chi nhánh"));
        if (itemRepository.existsByBranchIdAndName(branchId, request.name())) {
            throw BusinessException.conflict("Tên vật dụng đã tồn tại trong chi nhánh này");
        }
        assertDefaultQuantityWithinStock(request);

        Item item = new Item();
        item.setBranch(branch);
        item.setName(request.name());
        item.setPrice(request.price());
        item.setQuantityAvailable(request.quantityAvailable());
        item.setDefaultQuantityPerRoom(request.defaultQuantityPerRoom());
        return ItemResponse.from(itemRepository.save(item));
    }

    public ItemResponse update(Long id, ItemRequest request) {
        Item item = findEntity(id);
        SecurityUtils.assertCanAccessBranch(item.getBranch().getId());
        if (!item.getName().equals(request.name())
                && itemRepository.existsByBranchIdAndNameAndIdNot(item.getBranch().getId(), request.name(), id)) {
            throw BusinessException.conflict("Tên vật dụng đã tồn tại trong chi nhánh này");
        }
        assertDefaultQuantityWithinStock(request);

        item.setName(request.name());
        item.setPrice(request.price());
        item.setQuantityAvailable(request.quantityAvailable());
        item.setDefaultQuantityPerRoom(request.defaultQuantityPerRoom());
        return ItemResponse.from(itemRepository.save(item));
    }

    public void delete(Long id) {
        Item item = findEntity(id);
        SecurityUtils.assertCanAccessBranch(item.getBranch().getId());
        if (handoverItemRepository.existsByItemId(id)) {
            throw BusinessException.conflict("Không thể xóa vật dụng đang được dùng trong vật dụng bàn giao của loại phòng");
        }
        itemRepository.deleteById(id);
    }

    /**
     * Decrements branch stock when a unit is marked "Mất" at checkout — floors at 0 rather than
     * failing, since a physically-lost item should never make the recorded stock go negative.
     */
    public void decrementStock(Long itemId, int quantityLost) {
        if (quantityLost <= 0) {
            return;
        }
        Item item = findEntity(itemId);
        item.setQuantityAvailable(Math.max(0, item.getQuantityAvailable() - quantityLost));
        itemRepository.save(item);
    }

    /**
     * Verifies that, after simulating the given per-room-type room-count changes for a branch
     * (e.g. {@code {newRoomTypeId: +1}} for creating a room, or {@code {oldTypeId: -1, newTypeId: +1}}
     * for changing a room's type), no item's total demand across every room type of that branch
     * would exceed its {@code quantityAvailable}. Demand for an item = sum over every room type of
     * the branch of (room count of that type) x (that type's configured quantity of the item).
     */
    @Transactional(readOnly = true)
    public void assertSufficientStock(Long branchId, Map<Long, Integer> roomTypeCountDeltas) {
        Map<Long, Long> roomCountsByType = roomRepository.findByBranchId(branchId).stream()
                .collect(Collectors.groupingBy(room -> room.getRoomType().getId(), Collectors.counting()));

        Map<Long, Long> simulatedCounts = new HashMap<>(roomCountsByType);
        roomTypeCountDeltas.forEach((roomTypeId, delta) ->
                simulatedCounts.merge(roomTypeId, (long) delta, Long::sum));

        List<RoomTypeHandoverItem> handoverItems = handoverItemRepository.findByRoomType_BranchId(branchId);

        Map<Long, Long> demandByItemId = new HashMap<>();
        Map<Long, Item> itemsById = new HashMap<>();
        for (RoomTypeHandoverItem handoverItem : handoverItems) {
            long roomCount = simulatedCounts.getOrDefault(handoverItem.getRoomType().getId(), 0L);
            if (roomCount <= 0) {
                continue;
            }
            Item item = handoverItem.getItem();
            itemsById.put(item.getId(), item);
            demandByItemId.merge(item.getId(), roomCount * handoverItem.getQuantity(), Long::sum);
        }

        for (Map.Entry<Long, Long> entry : demandByItemId.entrySet()) {
            Item item = itemsById.get(entry.getKey());
            if (entry.getValue() > item.getQuantityAvailable()) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "Không đủ \"" + item.getName() + "\" tại chi nhánh: cần " + entry.getValue()
                                + ", chỉ có " + item.getQuantityAvailable());
            }
        }
    }

    private void assertDefaultQuantityWithinStock(ItemRequest request) {
        if (request.defaultQuantityPerRoom() > request.quantityAvailable()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Số lượng mặc định mỗi phòng không được lớn hơn tổng số lượng đang có tại chi nhánh");
        }
    }

    private Item findEntity(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy vật dụng"));
    }
}
