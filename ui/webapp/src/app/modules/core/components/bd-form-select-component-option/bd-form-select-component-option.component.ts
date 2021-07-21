import { Component, ComponentFactoryResolver, ComponentRef, Input, OnInit, Type, ViewContainerRef } from '@angular/core';

@Component({
  selector: 'app-bd-form-select-component-option',
  templateUrl: './bd-form-select-component-option.component.html',
  styleUrls: ['./bd-form-select-component-option.component.css'],
})
export class BdFormSelectComponentOptionComponent<T, X> implements OnInit {
  @Input() option: T;
  @Input() componentType: Type<X>;
  private componentRef: ComponentRef<X>;

  constructor(private resolver: ComponentFactoryResolver, private vc: ViewContainerRef) {}

  ngOnInit(): void {
    this.vc.clear();
    const factory = this.resolver.resolveComponentFactory(this.componentType);
    this.componentRef = this.vc.createComponent(factory);
    this.componentRef.instance['option'] = this.option;
  }

  ngAfterViewInit(): void {}

  ngOnDestroy(): void {
    if (!this.componentRef) {
      this.componentRef.destroy();
    }
  }
}
