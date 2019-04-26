import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { HiveBrowserComponent } from './hive-browser.component';

describe('HiveBrowserComponent', () => {
  let component: HiveBrowserComponent;
  let fixture: ComponentFixture<HiveBrowserComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ HiveBrowserComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(HiveBrowserComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
