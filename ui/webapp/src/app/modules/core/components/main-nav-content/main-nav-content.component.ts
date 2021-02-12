import { animate, animateChild, group, state, style, transition, trigger } from '@angular/animations';
import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, HostBinding, OnInit } from '@angular/core';
import { isString } from 'lodash-es';
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
        'panelVisible-sm',
        style({ 'margin-left': '10px', 'margin-right': '310px', 'max-width': 'calc(100% - 10px - 310px)' })
      ),
      state(
        'panelVisible-lg',
        style({ 'margin-left': '110px', 'margin-right': '310px', 'max-width': 'calc(100% - 110px - 310px)' })
      ),
      state(
        'panelHidden-sm',
        style({ 'margin-left': '10px', 'margin-right': '10px', 'max-width': 'calc(100% - 10px - 10px)' })
      ),
      state(
        'panelHidden-lg',
        style({ 'margin-left': '110px', 'margin-right': '110px', 'max-width': 'calc(100% - 110px - 110px)' })
      ),
      transition('* => *', group([animate('0.2s ease'), animateChild()])),
    ]),
  ],
})
export class MainNavContentComponent implements OnInit {
  constructor(public areas: NavAreasService, private media: BreakpointObserver) {}

  @HostBinding('@marginForPanel') get marginAnimation() {
    if (this.media.isMatched('(min-width: 1280px)')) {
      return this.areas.panelVisible.value ? 'panelVisible-lg' : 'panelHidden-lg';
    } else {
      return this.areas.panelVisible.value ? 'panelVisible-sm' : 'panelHidden-sm';
    }
  }

  animationState: string;

  ngOnInit(): void {
    this.areas.primaryRoute.subscribe((route) => {
      if (!route) {
        this.animationState = '';
      } else {
        this.animationState = isString(route.component) ? route.component : route.component.name;
      }
    });
  }
}
