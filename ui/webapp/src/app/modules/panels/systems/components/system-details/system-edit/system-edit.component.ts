import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, finalize, Observable, Subscription } from 'rxjs';
import { SystemConfigurationDto } from 'src/app/models/gen.dtos';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from '../../../../../core/utils/dirty.utils';
import { SystemsEditService } from '../../../services/systems-edit.service';


import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormInputComponent } from '../../../../../core/components/bd-form-input/bd-form-input.component';
import { FormsModule } from '@angular/forms';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-system-edit',
    templateUrl: './system-edit.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdFormInputComponent, FormsModule, BdButtonComponent, AsyncPipe]
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
