import { Component, Input } from '@angular/core';
import { VariableConfiguration, VariableDescriptor } from 'src/app/models/gen.dtos';

@Component({
  selector: 'bd-variable-desc-card',
  templateUrl: './bd-variable-desc-card.component.html',
})
export class BdVariableDescCardComponent {
  @Input() descriptor: VariableDescriptor;
  @Input() variable: VariableConfiguration;
}
