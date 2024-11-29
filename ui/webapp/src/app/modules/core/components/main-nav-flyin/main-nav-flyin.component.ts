import { animate, state, style, transition, trigger } from '@angular/animations';
import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, HostBinding, OnInit, inject } from '@angular/core';
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
            transition('open <=> closed', animate('0.2s ease')),
        ]),
        trigger('flyInWidth', [
            state('normal', style({ width: '350px' })),
            state('max-lg', style({ width: 'calc(100% - 174px)' })),
            state('max-lg-menu', style({ width: 'calc(100% - 174px)' })),
            state('max-sm', style({ width: 'calc(100% - 74px)' })),
            state('max-sm-menu', style({ width: 'calc(100% - 220px)' })),
            transition('* => *', animate('0.2s ease')),
        ]),
    ],
    standalone: false
})
export class MainNavFlyinComponent implements OnInit {
  private readonly areas = inject(NavAreasService);
  private readonly media = inject(BreakpointObserver);

  protected panelContent = '';

  @HostBinding('attr.data-cy')
  @HostBinding('@openClose')
  get animationState() {
    return this.areas.panelVisible$.value ? 'open' : 'closed';
  }

  @HostBinding('@flyInWidth') get widthAnimationState() {
    if (!this.areas.panelMaximized$.value) {
      return 'normal';
    }

    // check max-width to check the same as content - otherwise *exactly* 1280px (UI test) behave differently.
    const large = !this.media.isMatched('(max-width: 1280px)');
    if (this.areas.menuMaximized$.value) {
      return large ? 'max-lg-menu' : 'max-sm-menu';
    }

    return large ? 'max-lg' : 'max-sm';
  }

  ngOnInit(): void {
    this.areas.panelRoute$.subscribe((route) => {
      if (!route) {
        this.panelContent = '';
      } else {
        this.panelContent = this.areas.getAnimationRouteId(route);
      }
    });
  }
}
