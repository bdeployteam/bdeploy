import { animate, state, style, transition, trigger } from '@angular/animations';
import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, HostBinding, OnInit } from '@angular/core';
import { routerAnimation } from '../../animations/special';
import { NavAreasService } from '../../services/nav-areas.service';

@Component({
  selector: 'app-main-nav-content',
  templateUrl: './main-nav-content.component.html',
  styleUrls: ['./main-nav-content.component.css'],
  animations: [
    routerAnimation,
    trigger('marginForPanel', [
      state(
        'panelVisible-xs',
        style({
          'margin-left': '74px',
          'margin-right': '10px',
          'max-width': 'calc(100% - 10px - 10px)',
          filter: 'brightness(75%)',
        })
      ),
      state('panelVisible-sm', style({ 'margin-left': '74px', 'margin-right': '360px', 'max-width': 'calc(100% - 10px - 360px)' })),
      state('panelVisible-lg', style({ 'margin-left': '174px', 'margin-right': '360px', 'max-width': 'calc(100% - 110px - 360px)' })),
      state('panelHidden-sm', style({ 'margin-left': '74px', 'margin-right': '10px', 'max-width': 'calc(100% - 10px - 10px)' })),
      state('panelHidden-lg', style({ 'margin-left': '174px', 'margin-right': '110px', 'max-width': 'calc(100% - 110px - 110px)' })),
      transition('* => *', animate('0.2s ease')),
    ]),
  ],
})
export class MainNavContentComponent implements OnInit {
  constructor(public areas: NavAreasService, private media: BreakpointObserver) {}

  @HostBinding('@marginForPanel') get marginAnimation() {
    if (this.media.isMatched('(max-width: 960px)') && this.areas.panelVisible$.value) {
      return 'panelVisible-xs';
    } else if (this.media.isMatched('(max-width: 1280px)')) {
      return this.areas.panelVisible$.value ? 'panelVisible-sm' : 'panelHidden-sm';
    } else {
      return this.areas.panelVisible$.value ? 'panelVisible-lg' : 'panelHidden-lg';
    }
  }

  animationState: string;

  ngOnInit(): void {
    this.areas.primaryRoute$.subscribe((route) => {
      if (!route) {
        this.animationState = '';
      } else {
        this.animationState = this.areas.getRouteId(route);
      }
    });
  }
}
