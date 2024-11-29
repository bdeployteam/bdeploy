import { ChangeDetectionStrategy, Component, HostBinding, Input, ViewEncapsulation } from '@angular/core';

@Component({
    selector: 'app-bd-expand-button',
    templateUrl: './bd-expand-button.component.html',
    styleUrls: ['./bd-expand-button.component.css'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
})
export class BdExpandButtonComponent {
  @Input() icon: string;
  @HostBinding('attr.data-cy') @Input() text: string;
  @Input() expanded = false;
}
