import { Component, OnInit, ViewChild } from '@angular/core';
import { MatSelectChange, MatToolbar } from '@angular/material';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthenticationService } from '../services/authentication.service';
import { ConfigService } from '../services/config.service';
import { HeaderTitleService } from '../services/header-title.service';
import { LoggingService, LogLevel } from '../services/logging.service';
import { ThemeService } from '../services/theme.service';


@Component({
  selector: 'app-main-nav',
  templateUrl: './main-nav.component.html',
  styleUrls: ['./main-nav.component.css'],
})
export class MainNavComponent implements OnInit {

  @ViewChild('mainToolbar') mainTb: MatToolbar;

  isAuth$: Observable<boolean> = this.authService.getTokenSubject().pipe(map(s => s !== null));

  backendVersion: BehaviorSubject<string> = new BehaviorSubject('No connection');

  constructor(
    private authService: AuthenticationService,
    private cfgService: ConfigService,
    private router: Router,
    public theme: ThemeService,
    public title: HeaderTitleService,
    public logging: LoggingService
  ) {}

  ngOnInit(): void {
    this.authService.getTokenSubject().subscribe(v => {
      if (this.authService.isAuthenticated()) {
        this.cfgService.getBackendVersion().subscribe(ver => {
          this.backendVersion.next(ver);
        });
      }
    });
  }

  logout(): void {
    this.router.navigate(['/login']).then(result => {
      if (result) {
        this.authService.logout();
      }
    });
  }

  getLogLevel() {
    return this.logging.getLogger(null).getLogLevel().toString();
  }

  setLogLevel(event: MatSelectChange) {
    const lvl = +event.value as LogLevel;
    this.logging.getLogger(null).setLogLevel(lvl);
  }

}
