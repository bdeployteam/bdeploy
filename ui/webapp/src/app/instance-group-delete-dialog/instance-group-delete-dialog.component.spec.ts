import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { InstanceGroupDeleteDialogComponent } from './instance-group-delete-dialog.component';

describe('InstanceGroupDeleteDialogComponent', () => {
  let component: InstanceGroupDeleteDialogComponent;
  let fixture: ComponentFixture<InstanceGroupDeleteDialogComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ InstanceGroupDeleteDialogComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(InstanceGroupDeleteDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
