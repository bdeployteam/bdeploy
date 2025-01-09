import { Component, Input } from '@angular/core';
import { PopupPosition } from 'src/app/modules/core/components/bd-popup/bd-popup.directive';
import { AllFields, ConfigDescCardsComponent } from '../config-desc-cards/config-desc-cards.component';
import { BdPopupDirective } from '../../../../core/components/bd-popup/bd-popup.directive';

@Component({
    selector: 'app-config-desc-element',
    templateUrl: './config-desc-element.component.html',
    imports: [ConfigDescCardsComponent, BdPopupDirective]
})
export class ConfigDescElementComponent {
  @Input() card: AllFields;
  @Input() position: PopupPosition = 'above-right';
}
