import { Component, HostBinding, Input, OnInit } from '@angular/core';
import { MatSelectChange } from '@angular/material/select';
import { delayedFadeIn, delayedFadeOut } from '../../animations/fades';
import { easeX } from '../../animations/positions';
import { scaleWidthFromZero, scaleWidthToZero } from '../../animations/sizes';
import { AuthenticationService } from '../../services/authentication.service';
import { ConfigService } from '../../services/config.service';
import { LoggingService, LogLevel } from '../../services/logging.service';
import { NavAreasService } from '../../services/nav-areas.service';

@Component({
  selector: 'app-main-nav-menu',
  templateUrl: './main-nav-menu.component.html',
  styleUrls: ['./main-nav-menu.component.css'],
  animations: [delayedFadeIn, delayedFadeOut, easeX, scaleWidthFromZero, scaleWidthToZero],
})
export class MainNavMenuComponent implements OnInit {
  @Input() set expanded(val: boolean) {
    this.areas.menuMaximized.next(!this.areas.menuMaximized.value);
  }

  get expanded() {
    return this.areas.menuMaximized.value;
  }

  constructor(
    public cfgService: ConfigService,
    public logging: LoggingService,
    public authService: AuthenticationService,
    public areas: NavAreasService
  ) {}

  ngOnInit(): void {}

  @HostBinding('class') get hostClasses() {
    return this.expanded ? 'main-nav-menu-expanded' : 'main-nav-menu-collapsed';
  }

  getLogLevel() {
    return this.logging.getLogger(null).getLogLevel().toString();
  }

  setLogLevel(event: MatSelectChange) {
    const lvl = +event.value as LogLevel;
    this.logging.getLogger(null).setLogLevel(lvl);
  }
}
