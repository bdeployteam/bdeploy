import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, finalize, map, Observable, skipWhile, startWith, Subscription, tap } from 'rxjs';
import { Actions, CreateMultiNodeDto, OperatingSystem } from 'src/app/models/gen.dtos';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { NodesAdminService } from 'src/app/modules/primary/admin/services/nodes-admin.service';


import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { IdentifierValidator } from '../../../../core/validators/identifier.directive';
import { EditUniqueValueValidatorDirective } from '../../../../core/validators/edit-unique-value.directive';
import { RevalidateOnDirective } from '../../../../core/directives/revalidate-on.directive';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';
import { BdFormSelectComponent } from '../../../../core/components/bd-form-select/bd-form-select.component';

const DEF_MULTI_NODE: CreateMultiNodeDto = {
  name: '',
  config: {
    operatingSystem: OperatingSystem.UNKNOWN
  }
};

@Component({
  selector: 'app-add-multi-node',
  templateUrl: './add-multi-node.component.html',
  styleUrls: [],
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, BdFormInputComponent, IdentifierValidator, EditUniqueValueValidatorDirective, RevalidateOnDirective, BdButtonComponent, AsyncPipe, BdFormSelectComponent]
})
export class AddMultiNodeComponent implements DirtyableDialog, OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  private readonly actions = inject(ActionsService);
  protected readonly nodesAdmin = inject(NodesAdminService);

  private readonly adding$ = new BehaviorSubject<boolean>(false);

  protected nodeName$ = new BehaviorSubject<string>(null);
  protected nodeNames$ = this.nodesAdmin.nodes$.pipe(
    skipWhile((n) => !n?.length),
    map((n) => n.map((x) => x.name)),
    tap(() => setTimeout(() => this.form?.controls['name'].updateValueAndValidity())),
    startWith([])
  );
  protected mappedAdd$ = this.actions.action(
    [Actions.ADD_NODE],
    this.adding$,
    null,
    null,
    // the dummy string is to not react on *any* node beind manipulated from remote events until the user starts typing.
    this.nodeName$.pipe(map((n) => (!n?.length ? '__DUMMY__' : n)))
  );
  protected data = cloneDeep(DEF_MULTI_NODE);
  protected osOptions: OperatingSystem[] = this.nodesAdmin.getOperatingSystemOptions();

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;
  @ViewChild(NgForm) private readonly form: NgForm;

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected updateName(event: string) {
    this.data.name = event;
    this.nodeName$.next(event);
  }

  public isDirty(): boolean {
    return isDirty(this.data, DEF_MULTI_NODE);
  }

  public canSave(): boolean {
    return !this.form?.invalid;
  }

  public doSave(): Observable<unknown> {
    this.adding$.next(true);
    return this.nodesAdmin.addMultiNode(this.data).pipe(
      finalize(() => {
        this.adding$.next(false);
        this.nodeName$.next(null);
      })
    );
  }

  protected onSave() {
    this.doSave().subscribe(() => {
      this.data = cloneDeep(DEF_MULTI_NODE);
      this.tb.closePanel();
    });
  }

}
