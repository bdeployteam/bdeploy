import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { first, map } from 'rxjs/operators';
import { ACTION_NO, ACTION_OK, ACTION_YES, BdDialogMessage, BdDialogMessageComponent } from '../bd-dialog-message/bd-dialog-message.component';

/** Amount of pixels within which we trigger "near" events */
const SCROLL_PROXIMITY = 100;

export enum BdDialogScrollEvent {
  AT_TOP = 'atTop',
  NEAR_TOP = 'nearTop',
  NEAR_BOTTOM = 'nearBottom',
  AT_BOTTOM = 'atBottom',
}

@Component({
  selector: 'app-bd-dialog',
  templateUrl: './bd-dialog.component.html',
  styleUrls: ['./bd-dialog.component.css'],
})
export class BdDialogComponent implements OnInit, AfterViewInit, OnDestroy {
  @Input() loadingWhen$: Observable<boolean> = new BehaviorSubject<boolean>(false);
  @Input() resetWhen$ = new BehaviorSubject<any>(false);
  @Input() hideContentWhenLoading = true;
  @Input() restoreScrollAfterLoad = false;
  @Output() scrollTo = new EventEmitter<BdDialogScrollEvent>();

  /* template */ showContent$: Observable<boolean>;

  @ViewChild(BdDialogMessageComponent) messageComp: BdDialogMessageComponent;
  @ViewChild('scrollContainer') scrollContainer: ElementRef;

  private subscription: Subscription;
  private storedPosition: number;

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

    this.subscription.add(
      this.loadingWhen$.subscribe((loading) => {
        if (!this.restoreScrollAfterLoad) {
          return;
        }

        if (loading && !this.storedPosition) {
          this.storedPosition = this.scrollContainer.nativeElement.scrollTop;
        }
        if (!loading && !!this.storedPosition) {
          // after this view cycle to allow rendering of the loaded data into the content area.
          setTimeout(() => {
            this.scrollContainer.nativeElement.scrollTop = this.storedPosition;
            this.storedPosition = null;
          });
        }
      })
    );
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
      this.messageComp.result$.pipe(first()).subscribe((r) => {
        s.next(r);
        s.complete();
      });
    });
  }

  /* template */ onScrollContent(event: any) {
    const ele = event.target; // the scroll container;
    const scrollTop = ele.scrollTop; // the offset within the scrollHeight of the currently top-most pixel.
    const contentHeight = ele.scrollHeight; // the height of all the content which can scroll.
    const containerHeight = ele.clientHeight; // the height of the container which displayes the scrolled content.

    if (scrollTop === 0) {
      this.scrollTo.emit(BdDialogScrollEvent.AT_TOP);
    } else if (scrollTop < SCROLL_PROXIMITY) {
      this.scrollTo.emit(BdDialogScrollEvent.NEAR_TOP);
    }

    const remainingContentHeight = contentHeight - scrollTop; // the remaining height of the to-be-displayed content.
    if (remainingContentHeight === containerHeight) {
      this.scrollTo.emit(BdDialogScrollEvent.AT_BOTTOM);
    } else if (remainingContentHeight - containerHeight < SCROLL_PROXIMITY) {
      this.scrollTo.emit(BdDialogScrollEvent.NEAR_BOTTOM);
    }
  }
}
