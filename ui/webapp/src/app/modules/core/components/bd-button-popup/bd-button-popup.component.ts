import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ThemePalette } from '@angular/material/core';
import { TooltipPosition } from '@angular/material/tooltip';
import { PopupPosition } from '../bd-popup/bd-popup.directive';

@Component({
  selector: 'app-bd-button-popup',
  templateUrl: './bd-button-popup.component.html',
  styleUrls: ['./bd-button-popup.component.css'],
})
export class BdButtonPopupComponent implements OnInit {
  @Input() text: string;
  @Input() icon: string;
  @Input() badge: number;
  @Input() collapsed = true;
  @Input() tooltip: TooltipPosition;

  @Input() preferredPosition: PopupPosition = 'below-left';
  @Input() backdropClass: string;
  @Input() chevronColor: ThemePalette;

  @Output() popupOpened = new EventEmitter<BdButtonPopupComponent>();

  constructor() {}

  ngOnInit(): void {}
}
