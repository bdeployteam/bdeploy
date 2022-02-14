import { BreakpointObserver } from '@angular/cdk/layout';
import {
  Component,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { NavAreasService } from '../../services/nav-areas.service';
import { BdPanelButtonComponent } from '../bd-panel-button/bd-panel-button.component';

@Component({
  selector: 'app-bd-dialog-toolbar',
  templateUrl: './bd-dialog-toolbar.component.html',
})
export class BdDialogToolbarComponent implements OnInit, OnChanges, OnDestroy {
  /* template */ narrow$ = new BehaviorSubject<boolean>(true);

  @Input() header: string;
  @Input() panel = false;
  @Input() route: any[];
  @Input() relative = true;
  @Input() actionText = 'Back to Overview';
  @Input() actionIcon = 'arrow_back';

  @ViewChild('backButton', { static: false }) back: BdPanelButtonComponent;
  private subscription: Subscription;

  constructor(
    private title: Title,
    private areas: NavAreasService,
    bop: BreakpointObserver
  ) {
    this.subscription = combineLatest([
      bop.observe('(max-width: 800px)'),
      areas.panelMaximized$,
    ]).subscribe(([bs, max]) => {
      this.narrow$.next(bs.matches || !max);
    });
  }

  ngOnInit(): void {
    this.ngOnChanges();
  }

  ngOnChanges(): void {
    if (!this.panel) {
      this.title.setTitle(`BDeploy - ${this.header}`);
    }
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public closePanel() {
    if (this.route) {
      this.back.onClick();
    } else {
      this.areas.closePanel();
    }
  }
}
