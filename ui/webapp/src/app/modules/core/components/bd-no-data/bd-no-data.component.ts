import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { BdLogoComponent } from '../bd-logo/bd-logo.component';

@Component({
    selector: 'app-bd-no-data',
    templateUrl: './bd-no-data.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [BdLogoComponent]
})
export class BdNoDataComponent {
  @Input() header: string;
}
