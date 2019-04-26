import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { HiveComponent } from './hive.component';

describe('HiveComponent', () => {
  let component: HiveComponent;
  let fixture: ComponentFixture<HiveComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ HiveComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(HiveComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
