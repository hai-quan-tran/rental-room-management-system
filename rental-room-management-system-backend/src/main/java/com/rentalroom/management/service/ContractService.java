package com.rentalroom.management.service;

import com.rentalroom.management.dto.request.ContractCreateRequest;
import com.rentalroom.management.dto.request.ContractEndDateRequest;
import com.rentalroom.management.dto.request.ContractTenantRequest;
import com.rentalroom.management.dto.response.ContractDetailResponse;
import com.rentalroom.management.dto.response.ContractResponse;
import com.rentalroom.management.dto.response.TenantInContractResponse;
import com.rentalroom.management.entity.Contract;
import com.rentalroom.management.entity.ContractTenant;
import com.rentalroom.management.entity.ContractTenantId;
import com.rentalroom.management.entity.Room;
import com.rentalroom.management.entity.Tenant;
import com.rentalroom.management.enums.ContractStatus;
import com.rentalroom.management.enums.RoomStatus;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.ContractRepository;
import com.rentalroom.management.repository.ContractTenantRepository;
import com.rentalroom.management.repository.RoomRepository;
import com.rentalroom.management.repository.TenantRepository;
import com.rentalroom.management.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ContractService {

    private final ContractRepository contractRepository;
    private final ContractTenantRepository contractTenantRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final BranchService branchService;

    public ContractService(ContractRepository contractRepository,
                            ContractTenantRepository contractTenantRepository,
                            RoomRepository roomRepository,
                            TenantRepository tenantRepository,
                            BranchService branchService) {
        this.contractRepository = contractRepository;
        this.contractTenantRepository = contractTenantRepository;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
        this.branchService = branchService;
    }

    @Transactional(readOnly = true)
    public List<ContractResponse> listByRoom(Long roomId) {
        Room room = findRoom(roomId);
        SecurityUtils.assertCanAccessBranch(room.getBranch().getId());
        return contractRepository.findByRoomIdOrderByStartDateDesc(roomId).stream()
                .map(ContractResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractDetailResponse get(Long id) {
        Contract contract = findEntity(id);
        SecurityUtils.assertCanAccessBranch(contract.getRoom().getBranch().getId());
        return toDetail(contract);
    }

    public ContractDetailResponse create(Long roomId, ContractCreateRequest request) {
        Room room = findRoom(roomId);
        SecurityUtils.assertCanAccessBranch(room.getBranch().getId());
        if (room.getStatus() != RoomStatus.TRONG) {
            throw BusinessException.conflict("Phòng đang được thuê, không thể tạo hợp đồng mới");
        }

        long representativeCount = request.tenants().stream().filter(ContractTenantRequest::representative).count();
        if (representativeCount != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Hợp đồng phải có đúng 1 người đại diện ký hợp đồng");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày kết thúc phải sau ngày bắt đầu");
        }

        Contract contract = new Contract();
        contract.setRoom(room);
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setMonthlyRent(request.monthlyRent() != null ? request.monthlyRent() : room.getMonthlyRent());
        contract.setDepositAmount(request.depositAmount());
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setCreatedBy(SecurityUtils.currentUser().userId());
        contract.setUpdatedBy(SecurityUtils.currentUser().userId());
        Contract saved = contractRepository.save(contract);

        for (ContractTenantRequest tenantRequest : request.tenants()) {
            attachTenant(saved, tenantRequest.tenantId(), tenantRequest.representative());
        }

        room.setStatus(RoomStatus.DANG_THUE);
        roomRepository.save(room);
        branchService.evictRoomTypeSummary(room.getBranch().getId());

        return toDetail(saved);
    }

    public ContractDetailResponse addTenant(Long contractId, ContractTenantRequest request) {
        Contract contract = findEntity(contractId);
        SecurityUtils.assertCanAccessBranch(contract.getRoom().getBranch().getId());
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw BusinessException.conflict("Hợp đồng đã kết thúc, không thể thêm người thuê");
        }
        if (request.representative() && contractTenantRepository.existsById_ContractIdAndRepresentativeTrue(contractId)) {
            throw BusinessException.conflict("Hợp đồng này đã có người đại diện ký hợp đồng");
        }
        attachTenant(contract, request.tenantId(), request.representative());
        return toDetail(contract);
    }

    public ContractDetailResponse updateEndDate(Long contractId, ContractEndDateRequest request) {
        Contract contract = findEntity(contractId);
        SecurityUtils.assertCanAccessBranch(contract.getRoom().getBranch().getId());
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw BusinessException.conflict("Hợp đồng đã kết thúc, không thể sửa ngày kết thúc");
        }
        if (request.endDate() != null && request.endDate().isBefore(contract.getStartDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày kết thúc phải sau ngày bắt đầu");
        }
        contract.setEndDate(request.endDate());
        contract.setUpdatedBy(SecurityUtils.currentUser().userId());
        return toDetail(contract);
    }

    public ContractDetailResponse removeTenant(Long contractId, Long tenantId) {
        Contract contract = findEntity(contractId);
        SecurityUtils.assertCanAccessBranch(contract.getRoom().getBranch().getId());
        ContractTenantId id = new ContractTenantId(contractId, tenantId);
        ContractTenant contractTenant = contractTenantRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Người thuê không thuộc hợp đồng này"));

        if (contractTenantRepository.countById_ContractId(contractId) <= 1) {
            throw BusinessException.conflict("Hợp đồng phải có ít nhất 1 người thuê");
        }
        if (contractTenant.isRepresentative()) {
            throw BusinessException.conflict("Không thể xóa người đại diện ký hợp đồng — hãy đổi người đại diện trước");
        }
        contractTenantRepository.delete(contractTenant);
        return toDetail(contract);
    }

    private void attachTenant(Contract contract, Long tenantId, boolean representative) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy người thuê"));
        ContractTenant contractTenant = new ContractTenant();
        contractTenant.setId(new ContractTenantId(contract.getId(), tenantId));
        contractTenant.setContract(contract);
        contractTenant.setTenant(tenant);
        contractTenant.setRepresentative(representative);
        contractTenantRepository.save(contractTenant);
    }

    private ContractDetailResponse toDetail(Contract contract) {
        List<TenantInContractResponse> tenants = contractTenantRepository
                .findById_ContractIdOrderByRepresentativeDescJoinedAtAsc(contract.getId()).stream()
                .map(TenantInContractResponse::from)
                .toList();
        return new ContractDetailResponse(ContractResponse.from(contract), tenants);
    }

    private Room findRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy phòng"));
    }

    private Contract findEntity(Long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy hợp đồng"));
    }
}
