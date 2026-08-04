import { Component, computed } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { Role } from '../../core/enums/role.enum';
import { AuthService } from '../../core/services/auth.service';

interface NavItem {
  labelKey: string;
  icon: string;
  path: string;
  roles: Role[];
}

const NAV_ITEMS: NavItem[] = [
  { labelKey: 'NAV.DASHBOARD', icon: 'pi pi-chart-bar', path: '/dashboard', roles: [Role.ADMIN_TONG, Role.ADMIN_CAP_1] },
  { labelKey: 'NAV.ACCOUNTS', icon: 'pi pi-users', path: '/accounts', roles: [Role.ADMIN_TONG] },
  { labelKey: 'NAV.TENANTS', icon: 'pi pi-id-card', path: '/tenants', roles: [Role.ADMIN_TONG, Role.ADMIN_CAP_1] },
  { labelKey: 'NAV.BRANCHES', icon: 'pi pi-building', path: '/branches', roles: [Role.ADMIN_TONG] },
  { labelKey: 'NAV.ROOMS', icon: 'pi pi-home', path: '/rooms', roles: [Role.ADMIN_TONG, Role.ADMIN_CAP_1] },
  { labelKey: 'NAV.ROOM_TYPES', icon: 'pi pi-list', path: '/room-types', roles: [Role.ADMIN_TONG, Role.ADMIN_CAP_1] },
  { labelKey: 'NAV.ITEMS', icon: 'pi pi-box', path: '/items', roles: [Role.ADMIN_TONG, Role.ADMIN_CAP_1] },
  { labelKey: 'NAV.DEBT_RECORDS', icon: 'pi pi-wallet', path: '/debt-records', roles: [Role.ADMIN_TONG, Role.ADMIN_CAP_1] },
  { labelKey: 'NAV.MONTHLY_BILLS', icon: 'pi pi-receipt', path: '/monthly-bills', roles: [Role.ADMIN_TONG, Role.ADMIN_CAP_1] },
];

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './app-sidebar.html',
  styleUrl: './app-sidebar.scss',
})
export class AppSidebar {
  readonly navItems = computed(() => {
    const role = this.authService.currentUser()?.role;
    return NAV_ITEMS.filter((item) => !!role && item.roles.includes(role));
  });

  constructor(private readonly authService: AuthService) {}
}
