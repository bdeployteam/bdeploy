import { Component, Input } from '@angular/core';
import { VariableValue } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-variable-desc-card',
  templateUrl: './variable-desc-card.component.html',
})
export class VariableDescCardComponent {
  @Input() id: string;
  @Input() variable: VariableValue;
}
