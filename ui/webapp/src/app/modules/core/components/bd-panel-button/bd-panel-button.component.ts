import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { TooltipPosition } from '@angular/material/tooltip';
import { ActivatedRouteSnapshot, RouterLink, RouterLinkActive } from '@angular/router';
import { NavAreasService } from '../../services/nav-areas.service';
import { BdButtonColorMode } from '../bd-button/bd-button.component';

@Component({
  selector: 'app-bd-panel-button',
  templateUrl: './bd-panel-button.component.html',
  styleUrls: ['./bd-panel-button.component.css'],
})
export class BdPanelButtonComponent implements OnInit {
  @Input() icon: string;
  @Input() svgIcon: string;
  @Input() text: string;
  @Input() route: any[] = ['.'];
  @Input() relative = false;
  @Input() toggle = true;
  @Input() collapsed = true;
  @Input() color: BdButtonColorMode;
  @Input() disabled = false;
  @Input() tooltip: TooltipPosition;

  @ViewChild(RouterLink) private rl: RouterLink;
  @ViewChild(RouterLinkActive) /* template */ rla: RouterLinkActive;

  constructor(public areas: NavAreasService) {}

  ngOnInit(): void {}

  /* template */ getRoute() {
    if (this.relative) {
      const url = this.getFullPanelUrl(this.areas.panelRoute$.value);
      const rel = [...this.route];
      while (rel[0] === '..') {
        url.pop();
        rel.shift();
      }
      return [...url, ...rel];
    }
    return this.route;
  }

  private getFullPanelUrl(snap: ActivatedRouteSnapshot): string[] {
    if (!snap) {
      return [];
    }
    const result = !!snap.parent ? this.getFullPanelUrl(snap.parent) : [];
    return [...result, ...snap.url.map((u) => u.path)];
  }

  /* template */ toggleRoute(on: boolean) {
    if (!on) {
      this.areas.closePanel();
    }
  }

  /** Manually trigger the configured navigation */
  public onClick(): boolean {
    return this.rl.onClick();
  }
}
