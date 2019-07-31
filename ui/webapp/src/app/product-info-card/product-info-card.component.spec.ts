import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ProductInfoCardComponent } from './product-info-card.component';

describe('ProductInfoCardComponent', () => {
  let component: ProductInfoCardComponent;
  let fixture: ComponentFixture<ProductInfoCardComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ProductInfoCardComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ProductInfoCardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
