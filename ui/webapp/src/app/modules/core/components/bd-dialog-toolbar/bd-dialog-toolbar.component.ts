import { BreakpointObserver } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
} from '@angular/core';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { NavAreasService } from '../../services/nav-areas.service';
import { BdPanelButtonComponent } from '../bd-panel-button/bd-panel-button.component';

@Component({
  selector: 'app-bd-dialog-toolbar',
  templateUrl: './bd-dialog-toolbar.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdDialogToolbarComponent implements OnInit, OnChanges, OnDestroy {
  private readonly title = inject(Title);
  private readonly bop = inject(BreakpointObserver);
  protected readonly areas = inject(NavAreasService);

  protected narrow$ = new BehaviorSubject<boolean>(true);

  @Input() header: string;
  @Input() panel = false;
  @Input() route: unknown[];
  @Input() relative = true;
  @Input() actionText = 'Back to Overview';
  @Input() actionIcon = 'arrow_back';

  @ViewChild('backButton', { static: false }) back: BdPanelButtonComponent;
  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = combineLatest([this.bop.observe('(max-width: 800px)'), this.areas.panelMaximized$]).subscribe(
      ([bs, max]) => {
        this.narrow$.next(bs.matches || !max);
      },
    );

    this.ngOnChanges();
  }

  ngOnChanges(): void {
    if (!this.panel) {
      this.title.setTitle(`BDeploy - ${this.header}`);
    }
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public closePanel() {
    if (this.route) {
      this.back.onClick();
    } else {
      this.areas.closePanel();
    }
  }
}
