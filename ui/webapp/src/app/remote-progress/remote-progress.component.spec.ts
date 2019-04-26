import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { RemoteProgressComponent } from './remote-progress.component';


describe('RemoteProgressComponent', () => {
  let component: RemoteProgressComponent;
  let fixture: ComponentFixture<RemoteProgressComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ RemoteProgressComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(RemoteProgressComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
