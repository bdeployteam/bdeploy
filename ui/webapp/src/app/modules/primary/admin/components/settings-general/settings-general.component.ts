import { Component, OnDestroy, OnInit, ViewChild, ViewEncapsulation, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, Subject } from 'rxjs';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from '../../../../core/services/settings.service';

@Component({
  selector: 'app-settings-general',
  templateUrl: './settings-general.component.html',
  encapsulation: ViewEncapsulation.None,
})
export class SettingsGeneralComponent implements OnInit, OnDestroy, DirtyableDialog {
  private areas = inject(NavAreasService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  protected settings = inject(SettingsService);

  protected addPlugin$ = new Subject<any>();
  protected tabIndex: number;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  ngOnInit() {
    this.areas.registerDirtyable(this, 'admin');
    this.tabIndex = parseInt(this.route.snapshot.queryParamMap.get('tabIndex'));
  }

  ngOnDestroy(): void {
    this.router.navigate([], { queryParams: {} });
  }

  public isDirty(): boolean {
    return this.settings.isDirty();
  }

  public doSave(): Observable<any> {
    return this.settings.save();
  }

  protected tabChanged(tab) {
    this.router.navigate([], { queryParams: { tabIndex: tab.index } });
    this.tabIndex = parseInt(tab.index);
    if (this.areas.panelVisible$.value) {
      setTimeout(() => this.areas.closePanel());
    }
  }
}
