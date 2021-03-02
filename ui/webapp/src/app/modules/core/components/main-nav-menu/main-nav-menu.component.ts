import { animate, animateChild, group, query, state, style, transition, trigger } from '@angular/animations';
import { Component, HostBinding, Input, OnInit } from '@angular/core';
import { MatSelectChange } from '@angular/material/select';
import { delayedFadeIn, delayedFadeOut } from '../../animations/fades';
import { scaleWidthFromZero, scaleWidthToZero } from '../../animations/sizes';
import { AuthenticationService } from '../../services/authentication.service';
import { ConfigService } from '../../services/config.service';
import { LoggingService, LogLevel } from '../../services/logging.service';
import { NavAreasService } from '../../services/nav-areas.service';

@Component({
  selector: 'app-main-nav-menu',
  templateUrl: './main-nav-menu.component.html',
  styleUrls: ['./main-nav-menu.component.css', './main-nav-menu-hamburger.scss'],
  animations: [
    delayedFadeIn,
    delayedFadeOut,
    scaleWidthFromZero,
    scaleWidthToZero,
    trigger('menuOpenClose', [
      state('closed', style({ width: '64px' })),
      state('open', style({ width: '210px' })),
      transition('open => closed', [animate('0.2s ease', style({ width: '64px' }))]),
      transition('closed => open', [
        group([animate('0.2s ease', style({ width: '210px' })), query('@*', [animateChild()])]),
      ]),
    ]),
    trigger('headerOpenClose', [
      state('closed', style({ width: '104px' })),
      state('open', style({ width: '182px' })),
      transition('open => closed', [animate('0.2s ease')]),
      transition('closed => open', [animate('0.2s ease')]),
    ]),
  ],
})
export class MainNavMenuComponent implements OnInit {
  @Input() set expanded(val: boolean) {
    this.areas.menuMaximized$.next(!this.areas.menuMaximized$.value);
  }

  get expanded() {
    return this.areas.menuMaximized$.value;
  }

  constructor(
    public cfgService: ConfigService,
    public logging: LoggingService,
    public authService: AuthenticationService,
    public areas: NavAreasService
  ) {}

  ngOnInit(): void {}

  @HostBinding('@menuOpenClose') get animationState() {
    return this.expanded ? 'open' : 'closed';
  }

  getLogLevel() {
    return this.logging.getLogger(null).getLogLevel().toString();
  }

  setLogLevel(event: MatSelectChange) {
    const lvl = +event.value as LogLevel;
    this.logging.getLogger(null).setLogLevel(lvl);
  }
}
