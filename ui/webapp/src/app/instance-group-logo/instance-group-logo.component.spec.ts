import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { InstanceGroupLogoComponent } from './instance-group-logo.component';

describe('InstanceGroupLogoComponent', () => {
  let component: InstanceGroupLogoComponent;
  let fixture: ComponentFixture<InstanceGroupLogoComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ InstanceGroupLogoComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(InstanceGroupLogoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
