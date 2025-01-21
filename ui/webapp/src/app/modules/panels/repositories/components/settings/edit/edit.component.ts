import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { RepositoryDetailsService } from '../../../services/repository-details.service';


import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { FormsModule } from '@angular/forms';
import { BdFormInputComponent } from '../../../../../core/components/bd-form-input/bd-form-input.component';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';

@Component({
    selector: 'app-edit',
    templateUrl: './edit.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, BdFormInputComponent, BdButtonComponent]
})
export class EditComponent implements OnInit, OnDestroy, DirtyableDialog {
  private readonly areas = inject(NavAreasService);
  protected readonly repositories = inject(RepositoriesService);
  protected readonly details = inject(RepositoryDetailsService);

  protected saving$ = new BehaviorSubject<boolean>(false);
  protected repository: SoftwareRepositoryConfiguration;
  protected origRepository: SoftwareRepositoryConfiguration;
  protected disableSave: boolean;
  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.repositories.current$.subscribe((r) => {
      if (!r) {
        this.repository = null;
        return;
      }
      this.repository = cloneDeep(r);
      this.origRepository = cloneDeep(r);
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return isDirty(this.repository, this.origRepository);
  }

  protected onSave(): void {
    this.saving$.next(true);
    this.doSave().subscribe({
      next: () => {
        this.reset();
      },
      error: () => this.saving$.next(false),
    });
  }

  public doSave(): Observable<any> {
    return this.details.update(this.repository);
  }

  private reset() {
    this.saving$.next(false);
    this.repository = this.origRepository;
    this.tb.closePanel();
  }

  protected onChange() {
    this.disableSave = this.isDirty();
  }
}
