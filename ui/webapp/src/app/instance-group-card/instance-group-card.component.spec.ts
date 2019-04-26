import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { InstanceGroupCardComponent } from './instance-group-card.component';

describe('InstanceGroupCardComponent', () => {
  let component: InstanceGroupCardComponent;
  let fixture: ComponentFixture<InstanceGroupCardComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ InstanceGroupCardComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(InstanceGroupCardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
