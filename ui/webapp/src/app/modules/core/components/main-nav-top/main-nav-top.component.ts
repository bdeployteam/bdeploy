import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnInit } from '@angular/core';
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
  /* template */ logoSize = 64;
  /* template */ user: UserInfo;

  constructor(
    public cfgService: ConfigService,
    public search: SearchService,
    private media: BreakpointObserver,
    private authService: AuthenticationService,
    public settings: SettingsService
  ) {
    this.media.observe('(max-width:1280px)').subscribe((bs) => {
      this.logoSize = bs.matches ? 48 : 64;
    });
    this.authService.getUserInfo().subscribe((r) => {
      this.user = r;
    });
  }

  ngOnInit(): void {}
}
