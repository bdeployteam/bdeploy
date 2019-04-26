import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { InstanceAddEditComponent } from './instance-add-edit.component';

describe('InstanceAddEditComponent', () => {
  let component: InstanceAddEditComponent;
  let fixture: ComponentFixture<InstanceAddEditComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ InstanceAddEditComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(InstanceAddEditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
