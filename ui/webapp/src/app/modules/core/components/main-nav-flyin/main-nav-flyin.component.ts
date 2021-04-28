import { animate, animateChild, group, state, style, transition, trigger } from '@angular/animations';
import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, HostBinding, OnInit } from '@angular/core';
import { isString } from 'lodash-es';
import { Subscription } from 'rxjs';
import { routerAnimation } from '../../animations/special';
import { NavAreasService } from '../../services/nav-areas.service';

@Component({
  selector: 'app-main-nav-flyin',
  templateUrl: './main-nav-flyin.component.html',
  styleUrls: ['./main-nav-flyin.component.css'],
  animations: [
    routerAnimation,
    trigger('openClose', [
      state('open', style({ transform: 'translateX(0%)' })),
      state('closed', style({ transform: 'translateX(100%)' })),
      transition('open <=> closed', group([animate('0.2s ease'), animateChild()])),
    ]),
    trigger('flyInWidth', [
      state('normal', style({ width: '350px' })),
      state('max-lg', style({ width: 'calc(100% - 174px)' })),
      state('max-lg-menu', style({ width: 'calc(100% - 370px)' })),
      state('max-sm', style({ width: 'calc(100% - 74px)' })),
      state('max-sm-menu', style({ width: 'calc(100% - 220px)' })),
      transition('* => *', group([animateChild(), animate('0.2s ease')])),
    ]),
  ],
})
export class MainNavFlyinComponent implements OnInit {
  constructor(private areas: NavAreasService, private media: BreakpointObserver) {}

  @HostBinding('attr.data-cy')
  @HostBinding('@openClose')
  get animationState() {
    return this.areas.panelVisible$.value ? 'open' : 'closed';
  }

  @HostBinding('@flyInWidth') get widthAnimationState() {
    if (!this.areas.panelMaximized$.value) {
      return 'normal';
    }

    const large = this.media.isMatched('(min-width: 1280px)');
    if (this.areas.menuMaximized$.value) {
      return large ? 'max-lg-menu' : 'max-sm-menu';
    }

    return large ? 'max-lg' : 'max-sm';
  }

  panelContent = '';
  subscription: Subscription;

  ngOnInit(): void {
    this.subscription = this.areas.panelRoute$.subscribe((route) => {
      if (!route) {
        this.panelContent = '';
      } else {
        this.panelContent = isString(route.component) ? route.component : route.component.name;
      }
    });
  }
}
