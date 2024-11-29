import { Component, Input } from '@angular/core';
import { TemplateMessage } from '../instance-templates.component';

@Component({
    selector: 'app-template-message-details',
    templateUrl: './template-message-details.component.html',
    standalone: false
})
export class TemplateMessageDetailsComponent {
  @Input() record: TemplateMessage;
}
