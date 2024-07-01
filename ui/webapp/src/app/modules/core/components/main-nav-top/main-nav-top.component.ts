import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnInit, inject } from '@angular/core';
import { UserInfo } from 'src/app/models/gen.dtos';
import { AuthenticationService } from '../../services/authentication.service';
import { ConfigService } from '../../services/config.service';
import { SearchService } from '../../services/search.service';
import { SettingsService } from '../../services/settings.service';

@Component({
  selector: 'app-main-nav-top',
  templateUrl: './main-nav-top.component.html',
  styleUrls: ['./main-nav-top.component.css'],
})
export class MainNavTopComponent implements OnInit {
  private readonly media = inject(BreakpointObserver);
  private readonly authService = inject(AuthenticationService);
  protected cfgService = inject(ConfigService);
  protected search = inject(SearchService);
  protected settings = inject(SettingsService);

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
