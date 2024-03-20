import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { ThemePalette } from '@angular/material/core';
import { TooltipPosition } from '@angular/material/tooltip';
import { PopupPosition } from '../bd-popup/bd-popup.directive';

@Component({
  selector: 'app-bd-button-popup',
  templateUrl: './bd-button-popup.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdButtonPopupComponent {
  @Input() text: string;
  @Input() icon: string;
  @Input() badge: number;
  @Input() collapsed = true;
  @Input() tooltip: TooltipPosition;
  @Input() disabled = false;

  @Input() preferredPosition: PopupPosition = 'below-left';
  @Input() backdropClass: string;
  @Input() chevronColor: ThemePalette;

  @Output() popupOpened = new EventEmitter<BdButtonPopupComponent>();
}
