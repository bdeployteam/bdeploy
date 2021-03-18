import { Component, OnDestroy, OnInit, TemplateRef } from '@angular/core';
import { BehaviorSubject, Subject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { delayedFadeIn } from '../../animations/fades';

export interface BdDialogMessageAction<T> {
  /** The name of the action is usually rendered as button text */
  name: string;

  /** The value emitted as dialog result in case this action is chosen */
  result: T;

  /** Whether the action is a confirmation action, meaning it must be disabled if a confirmation value is given until the user correctly entered it */
  confirm: boolean;
}

export interface BdDialogMessage<T> {
  /** Header shown at the top of the message */
  header: string;

  /** Descriptive message */
  message?: string;

  /** Template for the message used *instead* of the message. */
  template?: TemplateRef<any>;

  /** Icon shown in the message, defaults to 'info' */
  icon?: string;

  /** An optional string which needs to be */
  confirmation?: string;

  /** A hint which tells the user which confirmation value needs to be matched. */
  confirmationHint?: string;

  /** A custom call */
  validation?: () => boolean;

  /** The actions which should be shown on the message */
  actions: BdDialogMessageAction<T>[];
}

export const ACTION_CONFIRM: BdDialogMessageAction<boolean> = { name: 'CONFIRM', result: true, confirm: true };
export const ACTION_APPLY: BdDialogMessageAction<boolean> = { name: 'APPLY', result: true, confirm: true };

export const ACTION_OK: BdDialogMessageAction<boolean> = { name: 'OK', result: true, confirm: true };
export const ACTION_CANCEL: BdDialogMessageAction<boolean> = { name: 'CANCEL', result: false, confirm: false };

export const ACTION_YES: BdDialogMessageAction<boolean> = { name: 'YES', result: true, confirm: true };
export const ACTION_NO: BdDialogMessageAction<boolean> = { name: 'NO', result: false, confirm: false };

@Component({
  selector: 'app-bd-dialog-message',
  templateUrl: './bd-dialog-message.component.html',
  styleUrls: ['./bd-dialog-message.component.css'],
  animations: [delayedFadeIn],
})
export class BdDialogMessageComponent implements OnInit, OnDestroy {
  public message$ = new BehaviorSubject<BdDialogMessage<any>>(null);
  public result$ = new Subject<any>();
  public confirmed$ = new BehaviorSubject<boolean>(true);

  private subscription: Subscription;

  private _userConfirmation;
  set userConfirmation(val: string) {
    if (val === this.message$.value.confirmation) {
      this.confirmed$.next(true);
    } else {
      this.confirmed$.next(false);
    }
    this._userConfirmation = val;
  }

  get userConfirmation(): string {
    return this._userConfirmation;
  }

  constructor() {
    // reset confirmation state whenever a new message arrives which requires confirmation.
    this.subscription = this.message$.pipe(filter((v) => !!v)).subscribe((r) => this.confirmed$.next(!r.confirmation));
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public reset(): void {
    if (!!this.message$.value) {
      this.result$.error('Dialog Message Reset');
      this.message$.next(null);
    }
  }

  /* template */ complete(result: any) {
    this.message$.next(null);
    this.result$.next(result);
  }

  /* template */ onConfirmationUpdate(value) {
    if (this.message$.value.confirmation === value) {
      this.confirmed$.next(true);
    }
  }
}
