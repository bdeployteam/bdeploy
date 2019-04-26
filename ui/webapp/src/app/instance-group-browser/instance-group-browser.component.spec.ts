import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { InstanceGroupBrowserComponent } from './instance-group-browser.component';

describe('InstanceGroupBrowserComponent', () => {
  let component: InstanceGroupBrowserComponent;
  let fixture: ComponentFixture<InstanceGroupBrowserComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ InstanceGroupBrowserComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(InstanceGroupBrowserComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
