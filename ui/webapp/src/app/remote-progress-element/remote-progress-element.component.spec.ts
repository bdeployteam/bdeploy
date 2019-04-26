import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { RemoteProgressElementComponent } from './remote-progress-element.component';


describe('RemoteProgressElementComponent', () => {
  let component: RemoteProgessElementComponent;
  let fixture: ComponentFixture<RemoteProgessElementComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ RemoteProgressElementComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(RemoteProgressElementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
