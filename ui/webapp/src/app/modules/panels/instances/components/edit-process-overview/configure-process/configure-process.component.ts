import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Observable, of, Subscription } from 'rxjs';
import { tap } from 'rxjs/operators';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProcessEditService } from '../../../services/process-edit.service';


import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { ConfigProcessHeaderComponent } from './config-process-header/config-process-header.component';
import { ConfigProcessParamGroupComponent } from './config-process-param-group/config-process-param-group.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-configure-process',
    templateUrl: './configure-process.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdButtonComponent, BdDialogContentComponent, ConfigProcessHeaderComponent, ConfigProcessParamGroupComponent, AsyncPipe]
})
export class ConfigureProcessComponent implements OnInit, OnDestroy, DirtyableDialog {
  private readonly instanceEdit = inject(InstanceEditService);
  private readonly areas = inject(NavAreasService);
  protected readonly edit = inject(ProcessEditService);

  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  protected hasPendingChanges: boolean;
  protected isInvalid: boolean;
  private isHeaderInvalid: boolean;
  private isParamGroupInvalid: boolean;

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return this.instanceEdit.hasPendingChanges();
  }

  public canSave(): boolean {
    return !this.isInvalid;
  }

  protected onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave(): Observable<unknown> {
    return of(true).pipe(
      tap(() => {
        this.edit.alignGlobalParameters(this.edit.application$.value, this.edit.process$.value);
        this.edit.recalculateStopCommand();
        this.instanceEdit.conceal(`Edit ${this.edit.process$.value.name}`);
      }),
    );
  }

  protected checkIsInvalid(event: boolean, isHeader: boolean) {
    if (isHeader) {
      this.isHeaderInvalid = event;
    } else {
      this.isParamGroupInvalid = event;
    }
    this.isInvalid = this.isHeaderInvalid || this.isParamGroupInvalid;
    this.hasPendingChanges = this.isDirty();
  }
}
