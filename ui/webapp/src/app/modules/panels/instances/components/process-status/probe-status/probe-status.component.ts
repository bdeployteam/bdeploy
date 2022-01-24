import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { ProcessProbeResultDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-probe-status',
  templateUrl: './probe-status.component.html',
  styleUrls: ['./probe-status.component.css'],
})
export class ProbeStatusComponent implements OnInit, OnChanges, OnDestroy {
  @Input() probe: ProcessProbeResultDto;

  /* template */ class: string;
  /* template */ icon = 'help';
  /* template */ content$ = new BehaviorSubject<string>(null);
  /* template */ narrow$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  constructor(bop: BreakpointObserver) {
    this.subscription = bop.observe('(max-width: 800px)').subscribe((bs) => {
      this.narrow$.next(bs.matches);
    });
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  ngOnChanges(changes: SimpleChanges): void {
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
