import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnInit, inject } from '@angular/core';
import { UserInfo } from 'src/app/models/gen.dtos';
import { AuthenticationService } from '../../services/authentication.service';
import { SearchService } from '../../services/search.service';
import { BdLogoComponent } from '../bd-logo/bd-logo.component';
import { BdSearchFieldComponent } from '../bd-search-field/bd-search-field.component';
import { BdPanelButtonComponent } from '../bd-panel-button/bd-panel-button.component';
import { UserAvatarComponent } from '../user-avatar/user-avatar.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-main-nav-top',
    templateUrl: './main-nav-top.component.html',
    styleUrls: ['./main-nav-top.component.css'],
    imports: [BdLogoComponent, BdSearchFieldComponent, BdPanelButtonComponent, UserAvatarComponent, AsyncPipe]
})
export class MainNavTopComponent implements OnInit {
  private readonly media = inject(BreakpointObserver);
  private readonly authService = inject(AuthenticationService);
  protected readonly search = inject(SearchService);

  protected logoSize = 64;
  protected user: UserInfo;

  ngOnInit() {
    this.media.observe('(max-width:1280px)').subscribe((bs) => {
      this.logoSize = bs.matches ? 48 : 64;
    });
    this.authService.getUserInfo().subscribe((r) => {
      this.user = r;
    });
  }
}
