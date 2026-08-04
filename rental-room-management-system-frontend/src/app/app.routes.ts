import { Routes } from '@angular/router';

import { Role } from './core/enums/role.enum';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login-page').then((m) => m.LoginPage),
  },
  {
    path: '',
    loadComponent: () => import('./layout/app-layout/app-layout').then((m) => m.AppLayout),
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard-page').then((m) => m.DashboardPage),
      },
      {
        path: 'accounts',
        loadComponent: () => import('./features/accounts/accounts-page').then((m) => m.AccountsPage),
        canActivate: [roleGuard(Role.ADMIN_TONG)],
      },
      {
        path: 'accounts/:id',
        loadComponent: () =>
          import('./features/accounts/account-detail/account-detail-page').then((m) => m.AccountDetailPage),
        canActivate: [roleGuard(Role.ADMIN_TONG)],
      },
      {
        path: 'tenants',
        loadComponent: () => import('./features/tenants/tenants-page').then((m) => m.TenantsPage),
      },
      {
        path: 'tenants/:id',
        loadComponent: () =>
          import('./features/tenants/tenant-detail/tenant-detail-page').then((m) => m.TenantDetailPage),
      },
      {
        path: 'branches',
        loadComponent: () => import('./features/branches/branches-page').then((m) => m.BranchesPage),
        canActivate: [roleGuard(Role.ADMIN_TONG)],
      },
      {
        path: 'branches/:id',
        loadComponent: () =>
          import('./features/branches/branch-detail/branch-detail-page').then((m) => m.BranchDetailPage),
        canActivate: [roleGuard(Role.ADMIN_TONG)],
      },
      {
        path: 'rooms',
        loadComponent: () => import('./features/rooms/rooms-page').then((m) => m.RoomsPage),
      },
      {
        path: 'rooms/new',
        loadComponent: () => import('./features/rooms/room-detail/room-detail-page').then((m) => m.RoomDetailPage),
        canActivate: [roleGuard(Role.ADMIN_TONG)],
      },
      {
        path: 'rooms/:id',
        loadComponent: () => import('./features/rooms/room-detail/room-detail-page').then((m) => m.RoomDetailPage),
        canActivate: [roleGuard(Role.ADMIN_TONG, Role.ADMIN_CAP_1)],
      },
      {
        path: 'room-types',
        loadComponent: () => import('./features/room-types/room-types-page').then((m) => m.RoomTypesPage),
        canActivate: [roleGuard(Role.ADMIN_TONG, Role.ADMIN_CAP_1)],
      },
      {
        path: 'room-types/:id',
        loadComponent: () =>
          import('./features/room-types/room-type-detail/room-type-detail-page').then((m) => m.RoomTypeDetailPage),
        canActivate: [roleGuard(Role.ADMIN_TONG, Role.ADMIN_CAP_1)],
      },
      {
        path: 'items',
        loadComponent: () => import('./features/items/items-page').then((m) => m.ItemsPage),
        canActivate: [roleGuard(Role.ADMIN_TONG, Role.ADMIN_CAP_1)],
      },
      {
        path: 'items/:id',
        loadComponent: () => import('./features/items/item-detail/item-detail-page').then((m) => m.ItemDetailPage),
        canActivate: [roleGuard(Role.ADMIN_TONG, Role.ADMIN_CAP_1)],
      },
      {
        path: 'debt-records',
        loadComponent: () => import('./features/debt-records/debt-records-page').then((m) => m.DebtRecordsPage),
      },
      {
        path: 'monthly-bills',
        loadComponent: () => import('./features/monthly-bills/monthly-bills-page').then((m) => m.MonthlyBillsPage),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
