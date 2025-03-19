import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { VariableConfiguration, VariableDescriptor } from 'src/app/models/gen.dtos';
import { MatCard } from '@angular/material/card';

@Component({
    selector: 'app-bd-variable-desc-card',
    templateUrl: './bd-variable-desc-card.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatCard]
})
export class BdVariableDescCardComponent {
  @Input() descriptor: VariableDescriptor;
  @Input() variable: VariableConfiguration;
}
