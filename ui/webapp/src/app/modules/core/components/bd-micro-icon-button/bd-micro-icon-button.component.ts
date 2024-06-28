import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostBinding,
  Input,
  inject,
} from '@angular/core';
import { fromEvent } from 'rxjs';

@Component({
  selector: 'app-bd-micro-icon-button',
  templateUrl: './bd-micro-icon-button.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdMicroIconButtonComponent implements AfterViewInit {
  private readonly _elementRef = inject(ElementRef);

  @Input() disabled = false;

  @HostBinding('style.max-width.px')
  @HostBinding('style.max-height.px')
  @Input()
  size = 24;

  ngAfterViewInit(): void {
    fromEvent<MouseEvent>(this._elementRef.nativeElement, 'click', {
      capture: true,
    }).subscribe((event) => {
      if (this.disabled) {
        event.stopPropagation();
      }
    });
  }
}
