import { AfterViewInit, Component, ElementRef, Input, OnInit } from '@angular/core';
import { fromEvent } from 'rxjs';

@Component({
  selector: 'app-bd-micro-icon-button',
  templateUrl: './bd-micro-icon-button.component.html',
  styleUrls: ['./bd-micro-icon-button.component.css'],
})
export class BdMicroIconButtonComponent implements OnInit, AfterViewInit {
  @Input() disabled = false;

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
