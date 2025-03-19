import {
  Component,
  ComponentRef,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  ViewContainerRef
} from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

export interface CellComponent<T, R> {
  record: T;
  column: BdDataColumn<T, R>;
}

@Component({
    selector: 'app-bd-data-component-cell',
    templateUrl: './bd-data-component-cell.component.html'
})
export class BdDataComponentCellComponent<T, R> implements OnInit, OnChanges, OnDestroy {
  private readonly vc = inject(ViewContainerRef);

  @Input() record: T;
  @Input() column: BdDataColumn<T, R>;
  private componentRef: ComponentRef<CellComponent<T, R>>;

  ngOnInit(): void {
    this.vc.clear();
    this.componentRef = this.vc.createComponent<CellComponent<T, R>>(this.column.component);
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
