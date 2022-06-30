import { Component, OnDestroy, ViewChild } from '@angular/core';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, finalize, Observable, Subscription } from 'rxjs';
import { SystemConfigurationDto } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from '../../../../../core/utils/dirty.utils';
import { SystemsEditService } from '../../../services/systems-edit.service';

@Component({
  selector: 'app-system-edit',
  templateUrl: './system-edit.component.html',
})
export class SystemEditComponent implements OnDestroy, DirtyableDialog {
  /* template */ system: SystemConfigurationDto;
  /* template */ orig: SystemConfigurationDto;
  /* template */ saving$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  constructor(private edit: SystemsEditService, areas: NavAreasService) {
    this.subscription = edit.current$.subscribe((c) => {
      this.system = cloneDeep(c);
      this.orig = cloneDeep(c);
    });
    this.subscription.add(areas.registerDirtyable(this, 'panel'));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public isDirty(): boolean {
    return isDirty(this.system, this.orig);
  }

  public doSave(): Observable<any> {
    this.saving$.next(true);
    return this.edit
      .update(this.system)
      .pipe(finalize(() => this.saving$.next(false)));
  }

  /* template */ onSave(): void {
    this.doSave().subscribe(() => {
      this.tb.closePanel();
    });
  }
}
