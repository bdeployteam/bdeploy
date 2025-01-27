import { ChangeDetectionStrategy, Component, HostBinding, Input, ViewEncapsulation } from '@angular/core';
import { MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle } from '@angular/material/expansion';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'app-bd-expand-button',
    templateUrl: './bd-expand-button.component.html',
    styleUrls: ['./bd-expand-button.component.css'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        MatExpansionPanel,
        MatExpansionPanelHeader,
        MatExpansionPanelTitle,
        MatIcon,
    ],
})
export class BdExpandButtonComponent {
  @Input() icon: string;
  @HostBinding('attr.data-testid') @Input() text: string;
  @Input() expanded = false;
}
