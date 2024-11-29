import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Observable, Subscription, finalize } from 'rxjs';
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
    standalone: false
})
export class SystemEditComponent implements OnInit, OnDestroy, DirtyableDialog {
  private readonly edit = inject(SystemsEditService);
  private readonly areas = inject(NavAreasService);

  protected system: SystemConfigurationDto;
  protected orig: SystemConfigurationDto;
  protected saving$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = this.edit.current$.subscribe((c) => {
      this.system = cloneDeep(c);
      this.orig = cloneDeep(c);
    });
    this.subscription.add(this.areas.registerDirtyable(this, 'panel'));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return isDirty(this.system, this.orig);
  }

  public doSave(): Observable<unknown> {
    this.saving$.next(true);
    return this.edit.update(this.system).pipe(finalize(() => this.saving$.next(false)));
  }

  protected onSave(): void {
    this.doSave().subscribe(() => {
      this.orig = this.system;
      this.tb.closePanel();
    });
  }
}
