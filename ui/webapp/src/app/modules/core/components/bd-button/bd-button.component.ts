import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  inject,
} from '@angular/core';
import { ThemePalette } from '@angular/material/core';
import { TooltipPosition } from '@angular/material/tooltip';
import { BehaviorSubject, Observable, fromEvent } from 'rxjs';

export type BdButtonColorMode = 'primary' | 'accent' | 'toolbar' | 'warn' | 'inherit';

@Component({
  selector: 'app-bd-button',
  templateUrl: './bd-button.component.html',
  styleUrls: ['./bd-button.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdButtonComponent implements AfterViewInit {
  private _elementRef = inject(ElementRef);

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
