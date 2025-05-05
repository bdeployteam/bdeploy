import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { ThemePalette } from '@angular/material/core';
import { TooltipPosition } from '@angular/material/tooltip';
import { PopupPosition, BdPopupDirective } from '../bd-popup/bd-popup.directive';
import { BdButtonComponent } from '../bd-button/bd-button.component';

@Component({
    selector: 'app-bd-button-popup',
    templateUrl: './bd-button-popup.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [BdButtonComponent, BdPopupDirective]
})
export class BdButtonPopupComponent {
  @Input() text: string;
  @Input() icon: string;
  @Input() badge: number;
  @Input() collapsed = true;
  @Input() tooltip: string;
  @Input() tooltipPosition: TooltipPosition;
  @Input() disabled = false;

  @Input() preferredPosition: PopupPosition = 'below-left';
  @Input() backdropClass: string;
  @Input() chevronColor: ThemePalette;

  @Output() popupOpened = new EventEmitter<BdButtonPopupComponent>();
}
