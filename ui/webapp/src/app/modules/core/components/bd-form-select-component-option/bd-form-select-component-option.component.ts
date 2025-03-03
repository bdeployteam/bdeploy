import { Component, ComponentRef, Input, OnDestroy, OnInit, Type, ViewContainerRef, inject } from '@angular/core';

export interface ComponentWithSelectedOption<T> {
  option: T;
}

@Component({
    selector: 'app-bd-form-select-component-option',
    templateUrl: './bd-form-select-component-option.component.html'
})
export class BdFormSelectComponentOptionComponent<T, X extends ComponentWithSelectedOption<T>> implements OnInit, OnDestroy {
  private readonly vc = inject(ViewContainerRef);

  @Input() option: T;
  @Input() componentType: Type<X>;
  private componentRef: ComponentRef<X>;

  ngOnInit(): void {
    this.vc.clear();
    this.componentRef = this.vc.createComponent<X>(this.componentType);
    this.componentRef.instance.option = this.option;
  }

  ngOnDestroy(): void {
    if (!this.componentRef) {
      this.componentRef.destroy();
    }
  }
}
