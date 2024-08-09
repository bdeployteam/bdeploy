import {
  Component,
  ComponentRef,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  Type,
  ViewContainerRef,
  inject,
} from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-bd-data-component-cell',
  templateUrl: './bd-data-component-cell.component.html',
})
export class BdDataComponentCellComponent<T, X> implements OnInit, OnChanges, OnDestroy {
  private readonly vc = inject(ViewContainerRef);

  @Input() record: T;
  @Input() column: BdDataColumn<T>;
  @Input() componentType: Type<X>;
  private componentRef: ComponentRef<X>;

  ngOnInit(): void {
    this.vc.clear();
    this.componentRef = this.vc.createComponent<X>(this.componentType);
    this.componentRef.instance['record'] = this.record;
    this.componentRef.instance['column'] = this.column;
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.record && this.componentRef?.instance) {
      this.componentRef.instance['record'] = changes.record.currentValue;
    }
  }

  ngOnDestroy(): void {
    this.componentRef?.destroy();
  }
}
