import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
} from '@angular/core';
import { TooltipPosition } from '@angular/material/tooltip';
import { ActivatedRouteSnapshot, RouterLink, RouterLinkActive } from '@angular/router';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { NavAreasService } from '../../services/nav-areas.service';
import { BdButtonColorMode, BdButtonComponent } from '../bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-bd-panel-button',
    templateUrl: './bd-panel-button.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [BdButtonComponent, RouterLinkActive, RouterLink, AsyncPipe]
})
export class BdPanelButtonComponent implements OnInit, OnDestroy, OnChanges {
  private readonly areas = inject(NavAreasService);

  @Input() icon: string;
  @Input() svgIcon: string;
  @Input() text: string;
  @Input() route: unknown[] = ['.'];
  @Input() relative = false;
  @Input() toggle = true;
  @Input() collapsed = true;
  @Input() color: BdButtonColorMode;
  @Input() disabled = false;
  @Input() tooltipPosition: TooltipPosition;
  @Input() loadingWhen$: Observable<boolean> = new BehaviorSubject<boolean>(false);

  @ViewChild(RouterLink) private readonly rl: RouterLink;
  @ViewChild(RouterLinkActive) public rla: RouterLinkActive;

  private subscription: Subscription;

  protected generatedRoute$ = new BehaviorSubject<unknown[]>([]);

  ngOnInit(): void {
    this.subscription = this.areas.panelRoute$.subscribe((snap) => {
      this.generatedRoute$.next(this.getRoute(snap));
    });
  }

  ngOnChanges(): void {
    this.generatedRoute$.next(this.getRoute(this.areas.panelRoute$.value));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
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
    const result = snap.parent ? this.getFullPanelUrl(snap.parent) : [];
    return [...result, ...snap.url.map((u) => u.path)];
  }

  protected toggleRoute() {
    if (this.toggle && this.rla.isActive) {
      this.areas.closePanel();
    }
  }

  /** Manually trigger the configured navigation */
  public onClick(): boolean {
    return this.rl.onClick(0, false, false, false, false);
  }
}
