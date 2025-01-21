import { Component, Input } from '@angular/core';
import { TemplateMessage } from '../instance-templates.component';
import { BdButtonPopupComponent } from '../../../../../../core/components/bd-button-popup/bd-button-popup.component';
import { BdNotificationCardComponent } from '../../../../../../core/components/bd-notification-card/bd-notification-card.component';

@Component({
    selector: 'app-template-message-details',
    templateUrl: './template-message-details.component.html',
    imports: [BdButtonPopupComponent, BdNotificationCardComponent]
})
export class TemplateMessageDetailsComponent {
  @Input() record: TemplateMessage;
}
