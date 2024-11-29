import { Component, Input } from '@angular/core';
import { PopupPosition } from 'src/app/modules/core/components/bd-popup/bd-popup.directive';
import { AllFields } from '../config-desc-cards/config-desc-cards.component';

@Component({
    selector: 'app-config-desc-element',
    templateUrl: './config-desc-element.component.html',
    standalone: false
})
export class ConfigDescElementComponent {
  @Input() card: AllFields;
  @Input() position: PopupPosition = 'above-right';
}
