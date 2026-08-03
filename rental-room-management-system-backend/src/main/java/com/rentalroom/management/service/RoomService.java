package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.RoomRequest;
import com.rentalroom.management.dto.response.BranchOptionResponse;
import com.rentalroom.management.dto.response.HandoverItemResponse;
import com.rentalroom.management.dto.response.RoomDetailResponse;
import com.rentalroom.management.dto.response.RoomResponse;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Room;
import com.rentalroom.management.entity.RoomType;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.enums.RoomStatus;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.ContractRepository;
import com.rentalroom.management.repository.RoomRepository;
import com.rentalroom.management.repository.RoomTypeHandoverItemRepository;
import com.rentalroom.management.repository.RoomTypeRepository;
import com.rentalroom.management.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class RoomService {

    private final RoomRepository roomRepository;
    private final BranchRepository branchRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomTypeHandoverItemRepository handoverItemRepository;
    private final ContractRepository contractRepository;
    private final BranchService branchService;

    public RoomService(RoomRepository roomRepository,
                        BranchRepository branchRepository,
                        RoomTypeRepository roomTypeRepository,
                        RoomTypeHandoverItemRepository handoverItemRepository,
                        ContractRepository contractRepository,
                        BranchService branchService) {
        this.roomRepository = roomRepository;
        this.branchRepository = branchRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.handoverItemRepository = handoverItemRepository;
        this.contractRepository = contractRepository;
        this.branchService = branchService;
    }

    @Transactional(readOnly = true)
    public PageResponse<RoomResponse> list(Long branchId, RoomStatus status, Long roomTypeId, String search, Pageable pageable) {
        SecurityUtils.assertCanAccessBranch(branchId);
        Specification<Room> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("branch").get("id"), branchId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (roomTypeId != null) {
                predicates.add(cb.equal(root.get("roomType").get("id"), roomTypeId));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("roomCode")), "%" + search.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PageResponse.from(roomRepository.findAll(spec, pageable).map(RoomResponse::from));
    }

    /** Lightweight branch lookup for the Rooms screen's branch picker — both roles, unlike the
     * ADMIN_TONG-only Branch management screen (BranchController). */
    @Transactional(readOnly = true)
    public List<BranchOptionResponse> branchOptions() {
        var user = SecurityUtils.currentUser();
        List<Branch> branches = user.role() == Role.ADMIN_TONG
                ? branchRepository.findAll()
                : branchRepository.findAllById(user.branchIds());
        return branches.stream().map(BranchOptionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RoomDetailResponse get(Long id) {
        Room room = findEntity(id);
        SecurityUtils.assertCanAccessBranch(room.getBranch().getId());
        List<HandoverItemResponse> items = handoverItemRepository
                .findByRoomTypeIdOrderByIdAsc(room.getRoomType().getId()).stream()
                .map(HandoverItemResponse::from)
                .toList();
        return new RoomDetailResponse(RoomResponse.from(room), items);
    }

    public RoomResponse create(Long branchId, RoomRequest request) {
        SecurityUtils.assertCanAccessBranch(branchId);
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy chi nhánh"));
        if (roomRepository.existsByBranchIdAndRoomCode(branchId, request.roomCode())) {
            throw BusinessException.conflict("Mã phòng đã tồn tại trong chi nhánh này");
        }
        RoomType roomType = findRoomType(request.roomTypeId());

        Room room = new Room();
        room.setBranch(branch);
        room.setRoomCode(request.roomCode());
        room.setRoomType(roomType);
        room.setMonthlyRent(request.monthlyRent());
        room.setStatus(RoomStatus.TRONG);
        Room saved = roomRepository.save(room);
        branchService.evictRoomTypeSummary(branchId);
        return RoomResponse.from(saved);
    }

    public RoomResponse update(Long id, RoomRequest request) {
        Room room = findEntity(id);
        SecurityUtils.assertCanAccessBranch(room.getBranch().getId());
        if (!room.getRoomCode().equals(request.roomCode())
                && roomRepository.existsByBranchIdAndRoomCodeAndIdNot(room.getBranch().getId(), request.roomCode(), id)) {
            throw BusinessException.conflict("Mã phòng đã tồn tại trong chi nhánh này");
        }
        if (!room.getRoomType().getId().equals(request.roomTypeId())
                && SecurityUtils.currentUser().role() != Role.ADMIN_TONG) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền thay đổi loại phòng của phòng này");
        }
        room.setRoomCode(request.roomCode());
        room.setRoomType(findRoomType(request.roomTypeId()));
        room.setMonthlyRent(request.monthlyRent());
        Room saved = roomRepository.save(room);
        branchService.evictRoomTypeSummary(room.getBranch().getId());
        return RoomResponse.from(saved);
    }

    public void delete(Long id) {
        Room room = findEntity(id);
        SecurityUtils.assertCanAccessBranch(room.getBranch().getId());
        if (room.getStatus() != RoomStatus.TRONG) {
            throw BusinessException.conflict("Chỉ có thể xóa phòng đang trống");
        }
        if (!contractRepository.findByRoomIdOrderByStartDateDesc(id).isEmpty()) {
            throw BusinessException.conflict("Không thể xóa phòng đã có lịch sử hợp đồng");
        }
        Long branchId = room.getBranch().getId();
        roomRepository.deleteById(id);
        branchService.evictRoomTypeSummary(branchId);
    }

    private RoomType findRoomType(Long roomTypeId) {
        return roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy loại phòng"));
    }

    private Room findEntity(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy phòng"));
    }
}
