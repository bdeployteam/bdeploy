import { Component, OnInit, ViewChild } from '@angular/core';
import { MatSelectChange } from '@angular/material/select';
import { MatToolbar } from '@angular/material/toolbar';
import { BehaviorSubject, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { convert2String } from 'src/app/modules/shared/utils/version.utils';
import { BackendInfoDto, MinionMode, Version } from '../../../../models/gen.dtos';
import { AuthenticationService } from '../../services/authentication.service';
import { ConfigService } from '../../services/config.service';
import { HeaderTitleService } from '../../services/header-title.service';
import { LoggingService, LogLevel } from '../../services/logging.service';
import { ThemeService } from '../../services/theme.service';


@Component({
  selector: 'app-main-nav',
  templateUrl: './main-nav.component.html',
  styleUrls: ['./main-nav.component.css'],
})
export class MainNavComponent implements OnInit {

  @ViewChild('mainToolbar', { static: true }) mainTb: MatToolbar;

  isAuth$: Observable<boolean> = this.authService.getTokenSubject().pipe(map(s => s !== null));

  backendInfo: BehaviorSubject<BackendInfoDto> = new BehaviorSubject({ version: null, mode: MinionMode.STANDALONE });

  constructor(
    public authService: AuthenticationService,
    private cfgService: ConfigService,
    public theme: ThemeService,
    public title: HeaderTitleService,
    public logging: LoggingService
  ) {}

  ngOnInit(): void {
    this.authService.getTokenSubject().subscribe(v => {
      if (this.authService.isAuthenticated()) {
        this.cfgService.getBackendInfo().subscribe(ver => {
          this.backendInfo.next(ver);
        });
      }
    });
  }

  needServerTypeHint() {
    if (this.backendInfo.value && this.backendInfo.value.mode !== MinionMode.STANDALONE) {
      return true;
    }
    return false;
  }

  getLogLevel() {
    return this.logging.getLogger(null).getLogLevel().toString();
  }

  setLogLevel(event: MatSelectChange) {
    const lvl = +event.value as LogLevel;
    this.logging.getLogger(null).setLogLevel(lvl);
  }

  formatVersion(version: Version) {
    if (version == null) {
      return 'Unknown';
    }
    return convert2String(version);
  }

}
