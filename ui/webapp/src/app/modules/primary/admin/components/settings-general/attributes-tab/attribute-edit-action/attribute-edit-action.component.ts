import { Component, Input } from '@angular/core';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-attribute-edit-action',
  templateUrl: './attribute-edit-action.component.html',
  standalone: false,
})
export class AttributeEditActionComponent {
  @Input() record: CustomAttributeDescriptor;
}
