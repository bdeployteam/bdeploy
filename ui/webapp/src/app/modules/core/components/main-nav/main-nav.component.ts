import { Component, OnInit, ViewChild } from '@angular/core';
import { MatSelectChange } from '@angular/material/select';
import { MatToolbar } from '@angular/material/toolbar';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { convert2String } from 'src/app/modules/shared/utils/version.utils';
import { MinionMode, Version } from '../../../../models/gen.dtos';
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

  constructor(
    public authService: AuthenticationService,
    public cfgService: ConfigService,
    public theme: ThemeService,
    public title: HeaderTitleService,
    public logging: LoggingService
  ) {}

  ngOnInit(): void {
  }

  needServerTypeHint() {
    return this.cfgService.config && this.cfgService.config.mode !== MinionMode.STANDALONE;
  }

  getLogLevel() {
    return this.logging.getLogger(null).getLogLevel().toString();
  }

  setLogLevel(event: MatSelectChange) {
    const lvl = +event.value as LogLevel;
    this.logging.getLogger(null).setLogLevel(lvl);
  }

  formatVersion(version: Version) {
    return convert2String(version);
  }

}
