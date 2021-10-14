import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';

@Component({
  selector: 'app-color-select',
  templateUrl: './color-select.component.html',
  styleUrls: ['./color-select.component.css'],
})
export class ColorSelectComponent implements OnInit {
  @Input() name: string;
  @Input() fg: string;
  @Input() bg: string;

  @Input() selected: boolean;
  @Output() selectedChanged = new EventEmitter<ColorSelectComponent>();

  constructor() {}

  ngOnInit(): void {}

  /* template */ onClick() {
    this.selectedChanged.emit(this);
  }
}
