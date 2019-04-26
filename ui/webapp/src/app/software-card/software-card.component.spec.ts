import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SoftwareCardComponent } from './software-card.component';

describe('SoftwareCardComponent', () => {
  let component: SoftwareCardComponent;
  let fixture: ComponentFixture<SoftwareCardComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SoftwareCardComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SoftwareCardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
