import {
  Component,
  EventEmitter,
  Input,
  Output,
  ViewEncapsulation,
} from '@angular/core';

@Component({
  selector: 'app-expression-toggle',
  templateUrl: './expression-toggle.component.html',
  styleUrls: ['./expression-toggle.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class ExpressionToggleComponent {
  @Input() link: boolean;
  @Input() disabled: boolean;
  @Output() linkChanged = new EventEmitter<boolean>();
}
