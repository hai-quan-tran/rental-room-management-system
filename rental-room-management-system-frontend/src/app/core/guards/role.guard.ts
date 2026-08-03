import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Role } from '../enums/role.enum';
import { AuthService } from '../services/auth.service';

/** Route factory: `canActivate: [roleGuard(Role.ADMIN_TONG)]` */
export const roleGuard = (...allowedRoles: Role[]): CanActivateFn => {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (authService.hasRole(...allowedRoles)) {
      return true;
    }

    return router.createUrlTree(['/dashboard']);
  };
};
