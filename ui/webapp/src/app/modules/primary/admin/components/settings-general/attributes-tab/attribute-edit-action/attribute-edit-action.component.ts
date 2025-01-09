import { Component, Input } from '@angular/core';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';
import { BdPanelButtonComponent } from '../../../../../../core/components/bd-panel-button/bd-panel-button.component';

@Component({
    selector: 'app-attribute-edit-action',
    templateUrl: './attribute-edit-action.component.html',
    imports: [BdPanelButtonComponent],
})
export class AttributeEditActionComponent {
  @Input() record: CustomAttributeDescriptor;
}
