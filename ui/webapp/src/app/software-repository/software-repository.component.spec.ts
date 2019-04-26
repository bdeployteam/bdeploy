import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SoftwareRepositoryComponent } from './software-repository.component';

describe('SoftwareRepositoryComponent', () => {
  let component: SoftwareRepositoryComponent;
  let fixture: ComponentFixture<SoftwareRepositoryComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SoftwareRepositoryComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SoftwareRepositoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
