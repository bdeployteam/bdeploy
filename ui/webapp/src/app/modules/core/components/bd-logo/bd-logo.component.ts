import { ChangeDetectionStrategy, Component, Input, ViewEncapsulation } from '@angular/core';

@Component({
    selector: 'app-bd-logo',
    templateUrl: './bd-logo.component.html',
    styleUrls: ['./bd-logo.component.css'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
})
export class BdLogoComponent {
  @Input() public size: number;
  @Input() public color: string;
}
