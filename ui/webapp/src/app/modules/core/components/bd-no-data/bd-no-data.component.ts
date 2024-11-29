import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
    selector: 'app-bd-no-data',
    templateUrl: './bd-no-data.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
})
export class BdNoDataComponent {
  @Input() header: string;
}
