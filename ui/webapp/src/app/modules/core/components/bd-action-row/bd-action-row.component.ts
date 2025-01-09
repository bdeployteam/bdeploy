import { Component, Input } from '@angular/core';

@Component({
    selector: 'app-bd-action-row',
    templateUrl: './bd-action-row.component.html'
})
export class BdActionRowComponent {
  @Input() align: 'left' | 'right' = 'right';
}
