import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigFilesBrowserComponent } from './config-files-browser.component';

describe('ConfigFilesBrowserComponent', () => {
  let component: ConfigFilesBrowserComponent;
  let fixture: ComponentFixture<ConfigFilesBrowserComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfigFilesBrowserComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigFilesBrowserComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
