import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
  ViewEncapsulation,
} from '@angular/core';

@Component({
  selector: 'app-bd-expression-toggle',
  templateUrl: './bd-expression-toggle.component.html',
  styleUrls: ['./bd-expression-toggle.component.css'],
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdExpressionToggleComponent {
  @Input() link: boolean;
  @Input() disabled: boolean;
  @Output() linkChanged = new EventEmitter<boolean>();
}
