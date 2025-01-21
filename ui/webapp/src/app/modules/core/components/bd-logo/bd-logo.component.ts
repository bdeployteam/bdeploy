import { ChangeDetectionStrategy, Component, Input, ViewEncapsulation } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { NgClass } from '@angular/common';

@Component({
    selector: 'app-bd-logo',
    templateUrl: './bd-logo.component.html',
    styleUrls: ['./bd-logo.component.css'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatIcon, NgClass]
})
export class BdLogoComponent {
  @Input() public size: number;
  @Input() public color: string;
}
