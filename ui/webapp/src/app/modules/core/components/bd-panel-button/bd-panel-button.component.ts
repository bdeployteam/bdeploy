import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { TooltipPosition } from '@angular/material/tooltip';
import { ActivatedRouteSnapshot, RouterLink, RouterLinkActive } from '@angular/router';
import { Subscription } from 'rxjs';
import { NavAreasService } from '../../services/nav-areas.service';
import { BdButtonColorMode } from '../bd-button/bd-button.component';

@Component({
  selector: 'app-bd-panel-button',
  templateUrl: './bd-panel-button.component.html',
  styleUrls: ['./bd-panel-button.component.css'],
})
export class BdPanelButtonComponent implements OnInit, OnDestroy, OnChanges {
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

  private subscription: Subscription;

  /* template */ generatedRoute;

  constructor(private areas: NavAreasService) {}

  ngOnInit(): void {
    this.subscription = this.areas.panelRoute$.subscribe((snap) => {
      this.generatedRoute = this.getRoute(snap);
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    this.generatedRoute = this.getRoute(this.areas.panelRoute$.value);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private getRoute(snap: ActivatedRouteSnapshot) {
    if (this.relative) {
      const url = this.getFullPanelUrl(snap);
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

  /* template */ toggleRoute() {
    if (this.toggle && this.rla.isActive) {
      this.areas.closePanel();
    }
  }

  /** Manually trigger the configured navigation */
  public onClick(): boolean {
    return this.rl.onClick();
  }
}
