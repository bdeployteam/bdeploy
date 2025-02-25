import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  inject,
  Input,
  Output
} from '@angular/core';
import { ThemePalette } from '@angular/material/core';
import { MatTooltip, TooltipPosition } from '@angular/material/tooltip';
import { BehaviorSubject, fromEvent, Observable } from 'rxjs';
import { MatButton } from '@angular/material/button';
import { AsyncPipe, NgClass } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { MatBadge } from '@angular/material/badge';
import { MatProgressSpinner } from '@angular/material/progress-spinner';

export type BdButtonColorMode = 'primary' | 'accent' | 'toolbar' | 'warn' | 'inherit';

@Component({
    selector: 'app-bd-button',
    templateUrl: './bd-button.component.html',
    styleUrls: ['./bd-button.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatButton, NgClass, MatTooltip, MatIcon, MatBadge, MatProgressSpinner, AsyncPipe]
})
export class BdButtonComponent implements AfterViewInit {
  private readonly _elementRef = inject(ElementRef);

  @Input() icon: string;
  @Input() svgIcon: string;
  @Input() text: string;
  @Input() tooltip: TooltipPosition;
  @Input() badge: string | number;
  @Input() badgeColor: ThemePalette = 'accent';
  @Input() collapsed = true;
  @Input() color: BdButtonColorMode;
  @Input() disabled = false;
  @Input() isSubmit = true; // default in HTML *is* submit.
  @Input() loadingWhen$: Observable<boolean> = new BehaviorSubject<boolean>(false);

  @Input() isToggle = false;
  @Input() toggleOnClick = true;
  @Input() toggle = false;
  @Output() toggleChange = new EventEmitter<boolean>();

  ngAfterViewInit() {
    /*
     * Click event handler for the button. we bind on the host, NOT on the button intentionally
     * as otherwise events will still be generated even if the button is disabled.
     */
    fromEvent<MouseEvent>(this._elementRef.nativeElement, 'click', {
      capture: true,
    }).subscribe((event) => {
      if (this.disabled) {
        event.stopPropagation();
        return;
      }

      if (this.isToggle && this.toggleOnClick) {
        this.toggle = !this.toggle;

        // the toggle change needs to be fired *after* the click event.
        setTimeout(() => {
          this.toggleChange.emit(this.toggle);
        });

        // we will not inhibit the click event here, as routerLink (etc.) will need it.
      }
    });
  }
}
