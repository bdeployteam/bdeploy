import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SoftwareRepositoriesBrowserComponent } from './software-repositories-browser.component';

describe('SoftwareRepositoriesBrowserComponent', () => {
  let component: SoftwareRepositoriesBrowserComponent;
  let fixture: ComponentFixture<SoftwareRepositoriesBrowserComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SoftwareRepositoriesBrowserComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SoftwareRepositoriesBrowserComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
