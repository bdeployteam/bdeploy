import { AfterViewInit, Component, ElementRef, HostBinding, Input, OnInit } from '@angular/core';
import { fromEvent } from 'rxjs';

@Component({
  selector: 'app-bd-micro-icon-button',
  templateUrl: './bd-micro-icon-button.component.html',
  styleUrls: ['./bd-micro-icon-button.component.css'],
})
export class BdMicroIconButtonComponent implements OnInit, AfterViewInit {
  @Input() disabled = false;

  @HostBinding('style.max-width.px') @HostBinding('style.max-height.px') @Input() size = 24;

  constructor(public _elementRef: ElementRef) {}

  ngOnInit(): void {}

  ngAfterViewInit(): void {
    fromEvent<MouseEvent>(this._elementRef.nativeElement, 'click', { capture: true }).subscribe((event) => {
      if (this.disabled) {
        event.stopPropagation();
      }
    });
  }
}
