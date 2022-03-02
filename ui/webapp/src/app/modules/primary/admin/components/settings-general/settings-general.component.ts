import {
  Component,
  OnDestroy,
  OnInit,
  ViewChild,
  ViewEncapsulation,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, Subject } from 'rxjs';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from '../../../../core/services/settings.service';

@Component({
  selector: 'app-settings-general',
  templateUrl: './settings-general.component.html',
  styleUrls: ['./settings-general.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class SettingsGeneralComponent
  implements OnInit, OnDestroy, DirtyableDialog
{
  /* template */ addPlugin$ = new Subject<any>();
  /* template */ tabIndex: number;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  constructor(
    public settings: SettingsService,
    private areas: NavAreasService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    areas.registerDirtyable(this, 'admin');
  }

  ngOnInit() {
    this.tabIndex = parseInt(this.route.snapshot.queryParamMap.get('tabIndex'));
  }

  public isDirty(): boolean {
    return this.settings.isDirty();
  }

  public doSave(): Observable<any> {
    return this.settings.save();
  }

  /* template */ tabChanged(tab) {
    this.router.navigate([], { queryParams: { tabIndex: tab.index } });
    this.tabIndex = parseInt(tab.index);
    if (this.areas.panelVisible$.value) {
      setTimeout(() => this.areas.closePanel());
    }
  }

  ngOnDestroy(): void {
    this.router.navigate([], { queryParams: {} });
  }
}
