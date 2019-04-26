import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { InstanceGroupAddEditComponent } from './instance-group-add-edit.component';

describe('InstanceGroupAddComponent', () => {
  let component: InstanceGroupAddEditComponent;
  let fixture: ComponentFixture<InstanceGroupAddEditComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ InstanceGroupAddEditComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(InstanceGroupAddEditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
