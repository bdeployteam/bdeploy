import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SoftwareRepositoryCardComponent } from './software-repository-card.component';

describe('SoftwareRepositoryCardComponent', () => {
  let component: SoftwareRepositoryCardComponent;
  let fixture: ComponentFixture<SoftwareRepositoryCardComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SoftwareRepositoryCardComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SoftwareRepositoryCardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
