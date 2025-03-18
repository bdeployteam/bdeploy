import {
  Component,
  ComponentRef,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  Type,
  ViewContainerRef
} from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

export interface TableCellDisplay<T> {
  record: T;
  column: BdDataColumn<T>;
}

@Component({
    selector: 'app-bd-data-component-cell',
    templateUrl: './bd-data-component-cell.component.html'
})
export class BdDataComponentCellComponent<T, X extends TableCellDisplay<T>> implements OnInit, OnChanges, OnDestroy {
  private readonly vc = inject(ViewContainerRef);

  @Input() record: T;
  @Input() column: BdDataColumn<T>;
  @Input() componentType: Type<X>;
  private componentRef: ComponentRef<X>;

  ngOnInit(): void {
    this.vc.clear();
    this.componentRef = this.vc.createComponent<X>(this.componentType);
    this.componentRef.instance.record = this.record;
    this.componentRef.instance.column = this.column;
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['record'] && this.componentRef?.instance) {
      this.componentRef.instance.record = changes['record'].currentValue;
    }
  }

  ngOnDestroy(): void {
    this.componentRef?.destroy();
  }
}
