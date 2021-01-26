import { animate, state, style, transition, trigger } from '@angular/animations';
import { Component, HostBinding, OnInit } from '@angular/core';
import { NavAreasService } from '../../services/nav-areas.service';

@Component({
  selector: 'app-main-nav-flyin',
  templateUrl: './main-nav-flyin.component.html',
  styleUrls: ['./main-nav-flyin.component.css'],
  animations: [
    trigger('openClose', [
      state('open', style({ transform: 'translateX(0%)' })),
      state('closed', style({ transform: 'translateX(100%)' })),
      transition('open <=> closed', [animate('0.2s ease')]),
    ]),
  ],
})
export class MainNavFlyinComponent implements OnInit {
  constructor(private areas: NavAreasService) {}

  @HostBinding('@openClose') get animationState() {
    return this.areas.panelVisible.value ? 'open' : 'closed';
  }

  @HostBinding('class') get hostClasses() {
    if (!this.areas.panelMaximized.value) {
      return [];
    }

    if (!this.areas.menuMaximized.value) {
      return ['main-nav-flyin-maximized'];
    }

    return ['main-nav-flyin-maximized-menu-maximized'];
  }

  ngOnInit(): void {}
}
