import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ProductTagCardComponent } from './product-tag-card.component';

describe('ProductTagCardComponent', () => {
  let component: ProductTagCardComponent;
  let fixture: ComponentFixture<ProductTagCardComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ProductTagCardComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ProductTagCardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
