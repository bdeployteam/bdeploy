import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { DataFilesBrowserComponent } from './data-files-browser.component';

describe('DataFilesBrowserComponent', () => {
  let component: DataFilesBrowserComponent;
  let fixture: ComponentFixture<DataFilesBrowserComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ DataFilesBrowserComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DataFilesBrowserComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
