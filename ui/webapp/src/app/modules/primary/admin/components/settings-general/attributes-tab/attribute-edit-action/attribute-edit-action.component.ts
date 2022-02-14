import { Component, Input } from '@angular/core';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'attribute-edit-action',
  templateUrl: './attribute-edit-action.component.html',
})
export class AttributeEditActionComponent {
  @Input() record: CustomAttributeDescriptor;
}
