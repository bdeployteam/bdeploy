import { HttpClient } from '@angular/common/http';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { RepositoryDetailsService } from '../../../services/repository-details.service';

@Component({
  selector: 'app-edit',
  templateUrl: './edit.component.html',
})
export class EditComponent implements OnInit, OnDestroy, DirtyableDialog {
  /* template */ saving$ = new BehaviorSubject<boolean>(false);
  /* template */ repository: SoftwareRepositoryConfiguration;
  /* template */ origRepository: SoftwareRepositoryConfiguration;
  /* template */ disableSave: boolean;
  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  constructor(
    public repositories: RepositoriesService,
    public details: RepositoryDetailsService,
    private http: HttpClient,
    areas: NavAreasService
  ) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {
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
    this.subscription.unsubscribe();
  }

  isDirty(): boolean {
    return isDirty(this.repository, this.origRepository);
  }

  /* template */ onSave(): void {
    this.saving$.next(true);
    this.doSave().subscribe({
      next: () => {
        this.reset();
      },
      error: () => this.saving$.next(false),
    });
  }

  /* template */ public doSave(): Observable<any> {
    return this.details.update(this.repository);
  }

  private reset() {
    this.saving$.next(false);
    this.repository = this.origRepository;
    this.tb.closePanel();
  }

  /* template */ onChange() {
    this.disableSave = this.isDirty();
  }
}
