import { Component, Input } from '@angular/core';
import { VariableConfiguration } from './../../../../../models/gen.dtos';

@Component({
  selector: 'app-variable-desc-card',
  templateUrl: './variable-desc-card.component.html',
})
export class VariableDescCardComponent {
  @Input() variable: VariableConfiguration;
}
