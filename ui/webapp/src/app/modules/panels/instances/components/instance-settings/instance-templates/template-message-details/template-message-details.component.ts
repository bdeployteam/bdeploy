import { Component, Input } from '@angular/core';
import { TemplateMessage } from '../instance-templates.component';
import { BdButtonPopupComponent } from '../../../../../../core/components/bd-button-popup/bd-button-popup.component';
import { BdNotificationCardComponent } from '../../../../../../core/components/bd-notification-card/bd-notification-card.component';
import { BdDataColumn } from '../../../../../../../models/data';
import {
  TableCellDisplay
} from '../../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-template-message-details',
    templateUrl: './template-message-details.component.html',
    imports: [BdButtonPopupComponent, BdNotificationCardComponent]
})
export class TemplateMessageDetailsComponent implements TableCellDisplay<TemplateMessage> {
  @Input() record: TemplateMessage;
  @Input() column: BdDataColumn<TemplateMessage>;
}
