package com.rentalroom.management.security;

import com.rentalroom.management.enums.Role;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.function.Supplier;

/** Reads the authenticated {@link UserPrincipal} set by {@link JwtAuthenticationFilter}. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Chưa đăng nhập");
        }
        return userPrincipal;
    }

    public static boolean isAdminTong() {
        return currentUser().role() == Role.ADMIN_TONG;
    }

    /** ADMIN_TONG can access every branch; ADMIN_CAP_1 only the branches it manages. */
    public static void assertCanAccessBranch(Long branchId) {
        UserPrincipal user = currentUser();
        if (user.role() == Role.ADMIN_TONG) {
            return;
        }
        if (!user.branchIds().contains(branchId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không quản lý chi nhánh này");
        }
    }

    /**
     * Resolves which branch IDs a request should be scoped to: ADMIN_CAP_1 is always locked to its
     * own managed branches (the client-supplied {@code requestedBranchId} is ignored for that
     * role); ADMIN_TONG gets just the requested branch, or every branch when none was requested.
     * {@code allBranchIdsSupplier} is only invoked in that last case, so callers can pass a lazy
     * {@code branchRepository.findAll()...} without paying for it when it isn't needed.
     */
    public static List<Long> resolveBranchScope(Long requestedBranchId, Supplier<List<Long>> allBranchIdsSupplier) {
        UserPrincipal user = currentUser();
        if (user.role() == Role.ADMIN_CAP_1) {
            return user.branchIds();
        }
        return requestedBranchId != null ? List.of(requestedBranchId) : allBranchIdsSupplier.get();
    }
}
