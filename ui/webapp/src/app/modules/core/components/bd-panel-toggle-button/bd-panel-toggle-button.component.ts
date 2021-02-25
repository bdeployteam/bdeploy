import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { RouterLinkActive } from '@angular/router';
import { NavAreasService } from '../../services/nav-areas.service';

@Component({
  selector: 'app-bd-panel-toggle-button',
  templateUrl: './bd-panel-toggle-button.component.html',
  styleUrls: ['./bd-panel-toggle-button.component.css'],
})
export class BdPanelToggleButtonComponent implements OnInit {
  @Input() icon: string;
  @Input() text: string;
  @Input() route: any[];

  @ViewChild(RouterLinkActive) /* template */ rla: RouterLinkActive;

  constructor(public areas: NavAreasService) {}

  ngOnInit(): void {}

  toggleRoute(on: boolean) {
    if (!on) {
      this.areas.closePanel();
    }
  }
}
