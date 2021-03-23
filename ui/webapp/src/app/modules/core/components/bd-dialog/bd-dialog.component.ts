import { AfterViewInit, Component, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { map } from 'rxjs/operators';
import { ACTION_NO, ACTION_OK, ACTION_YES, BdDialogMessage, BdDialogMessageComponent } from '../bd-dialog-message/bd-dialog-message.component';

@Component({
  selector: 'app-bd-dialog',
  templateUrl: './bd-dialog.component.html',
  styleUrls: ['./bd-dialog.component.css'],
})
export class BdDialogComponent implements OnInit, AfterViewInit, OnDestroy {
  @Input() loadingWhen$: Observable<boolean> = new BehaviorSubject<boolean>(false);
  @Input() resetWhen$ = new BehaviorSubject<any>(false);
  @Input() hideContentWhenLoading = true;

  /* template */ showContent$: Observable<boolean>;

  @ViewChild(BdDialogMessageComponent) messageComp: BdDialogMessageComponent;
  private subscription: Subscription;

  constructor() {}

  ngOnInit(): void {
    this.showContent$ = this.loadingWhen$.pipe(map((v) => !(v && this.hideContentWhenLoading)));
  }

  ngAfterViewInit(): void {
    this.subscription = this.resetWhen$.subscribe((r) => {
      if (r) {
        // currently only a dialog message would need to be reset.
        this.messageComp.reset();
      }
    });
  }

  ngOnDestroy(): void {
    if (!!this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  public info(header: string, message: string, icon?: string): Observable<boolean> {
    return this.message({ header, message, icon, actions: [ACTION_OK] });
  }

  public confirm(header: string, message: string, icon?: string, confirmation?: string, hint?: string): Observable<boolean> {
    return this.message({ header, message, icon, confirmation, confirmationHint: hint, actions: [ACTION_NO, ACTION_YES] });
  }

  public message<T>(msg: BdDialogMessage<T>): Observable<T> {
    return new Observable((s) => {
      this.messageComp.message$.next(msg);
      const sub = this.messageComp.result$.subscribe((r) => {
        sub.unsubscribe();
        s.next(r);
        s.complete();
      });
    });
  }
}
