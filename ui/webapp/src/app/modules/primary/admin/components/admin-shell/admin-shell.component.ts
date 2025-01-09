import { Component, inject } from '@angular/core';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { MatSidenavContainer, MatSidenav, MatSidenavContent } from '@angular/material/sidenav';
import { MatNavList, MatListItem } from '@angular/material/list';
import { RouterLinkActive, RouterLink, RouterOutlet } from '@angular/router';
import { MatIcon } from '@angular/material/icon';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-admin-shell',
    templateUrl: './admin-shell.component.html',
    styleUrls: ['./admin-shell.component.css'],
    imports: [MatSidenavContainer, MatSidenav, MatNavList, MatListItem, RouterLinkActive, RouterLink, MatIcon, MatSidenavContent, RouterOutlet, AsyncPipe]
})
export class AdminShellComponent {
  public readonly cfg = inject(ConfigService);
}
