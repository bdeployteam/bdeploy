import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { BehaviorSubject, combineLatest, finalize, map, Observable, Subscription } from 'rxjs';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { IdentifierValidator } from '../../../../core/validators/identifier.directive';
import { EditUniqueValueValidatorDirective } from '../../../../core/validators/edit-unique-value.directive';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';

@Component({
    selector: 'app-add-repository',
    templateUrl: './add-repository.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, BdFormInputComponent, IdentifierValidator, EditUniqueValueValidatorDirective, BdButtonComponent]
})
export class AddRepositoryComponent implements OnInit, OnDestroy, DirtyableDialog {
  private readonly repositories = inject(RepositoriesService);
  private readonly reports = inject(ReportsService);
  private readonly groups = inject(GroupsService);
  private readonly areas = inject(NavAreasService);

  protected saving$ = new BehaviorSubject<boolean>(false);
  protected repository: Partial<SoftwareRepositoryConfiguration> = {};
  protected usedNames: string[] = [];

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  ngOnInit() {
    this.subscription = this.areas.registerDirtyable(this, 'panel');

    combineLatest([
      this.groups.groups$.pipe(map((g) => g.map((x) => x.instanceGroupConfiguration.name))),
      this.repositories.repositories$.pipe(map((r) => r.map((y) => y.name))),
      this.reports.reports$.pipe(map((re) => re.map((z) => z.type))),
    ])
      .pipe(map(([g, r, re]) => [...g, ...r, ...re]))
      .subscribe((n) => {
        this.usedNames = n;
      });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return this.form.dirty;
  }

  public canSave(): boolean {
    return this.form.valid;
  }

  protected onSave() {
    this.saving$.next(true);
    this.doSave().subscribe(() => {
      this.areas.closePanel();
      this.subscription?.unsubscribe();
    });
  }

  public doSave(): Observable<SoftwareRepositoryConfiguration> {
    this.saving$.next(true);
    return this.repositories.create(this.repository).pipe(
      finalize(() => {
        this.saving$.next(false);
      }),
    );
  }
}
