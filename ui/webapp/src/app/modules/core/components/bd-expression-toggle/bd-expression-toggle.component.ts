import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, ViewEncapsulation } from '@angular/core';
import { MatButtonToggleGroup, MatButtonToggle } from '@angular/material/button-toggle';
import { ClickStopPropagationDirective } from '../../directives/click-stop-propagation.directive';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'app-bd-expression-toggle',
    templateUrl: './bd-expression-toggle.component.html',
    styleUrls: ['./bd-expression-toggle.component.css'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatButtonToggleGroup, ClickStopPropagationDirective, MatButtonToggle, MatTooltip, MatIcon]
})
export class BdExpressionToggleComponent {
  @Input() link: boolean;
  @Input() disabled: boolean;
  @Output() linkChanged = new EventEmitter<boolean>();
}
