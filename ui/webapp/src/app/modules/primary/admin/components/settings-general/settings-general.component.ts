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
  private readonly areas = inject(NavAreasService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly settings = inject(SettingsService);

  protected addPlugin$ = new Subject<unknown>();
  protected tabIndex: number;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  ngOnInit() {
    this.areas.registerDirtyable(this, 'admin');
    this.tabIndex = parseInt(this.route.snapshot.queryParamMap.get('tabIndex'), 10);
  }

  ngOnDestroy(): void {
    this.settings.discard();
  }

  public isDirty(): boolean {
    return this.settings.isDirty();
  }

  public doSave(): Observable<unknown> {
    return this.settings.save();
  }

  protected tabChanged(tab) {
    this.router.navigate([], { queryParams: { tabIndex: tab.index } });
    this.tabIndex = parseInt(tab.index, 10);
    if (this.areas.panelVisible$.value) {
      setTimeout(() => this.areas.closePanel());
    }
  }
}
