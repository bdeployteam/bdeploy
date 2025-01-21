import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, Input, OnChanges, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { ProcessProbeResultDto } from 'src/app/models/gen.dtos';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { NgClass, AsyncPipe, DatePipe } from '@angular/common';
import { BdButtonPopupComponent } from '../../../../../core/components/bd-button-popup/bd-button-popup.component';
import { MatCard } from '@angular/material/card';
import { MatDivider } from '@angular/material/divider';
import { BdEditorComponent } from '../../../../../core/components/bd-editor/bd-editor.component';

@Component({
    selector: 'app-probe-status',
    templateUrl: './probe-status.component.html',
    styleUrls: ['./probe-status.component.css'],
    imports: [MatIcon, MatTooltip, NgClass, BdButtonPopupComponent, MatCard, MatDivider, BdEditorComponent, AsyncPipe, DatePipe]
})
export class ProbeStatusComponent implements OnInit, OnChanges, OnDestroy {
  private readonly bop = inject(BreakpointObserver);

  @Input() probe: ProcessProbeResultDto;

  protected class: string;
  protected icon = 'help';
  protected content$ = new BehaviorSubject<string>(null);
  protected narrow$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = this.bop.observe('(max-width: 800px)').subscribe((bs) => {
      this.narrow$.next(bs.matches);
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  ngOnChanges(): void {
    const isBad = this.probe.status < 200 || this.probe.status >= 400;
    this.class = isBad ? 'local-bad' : 'local-good';
    this.icon = isBad ? 'heart_broken' : 'favorite';

    let msg = this.probe.message;

    // in case the response is JSON, we want to format that a little.
    try {
      msg = JSON.stringify(JSON.parse(this.probe.message), null, 2);
    } catch (e) {
      // ignore
    }

    this.content$.next(msg);
  }
}
