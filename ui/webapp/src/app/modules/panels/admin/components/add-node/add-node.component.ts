import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, finalize, map, Observable, of, skipWhile, startWith, Subscription, tap } from 'rxjs';
import { Actions, MinionMode, NodeAttachDto } from 'src/app/models/gen.dtos';
import {
  ACTION_CANCEL,
  BdDialogMessageAction
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
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
import { BdFileDropComponent } from '../../../../core/components/bd-file-drop/bd-file-drop.component';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { IdentifierValidator } from '../../../../core/validators/identifier.directive';
import { EditUniqueValueValidatorDirective } from '../../../../core/validators/edit-unique-value.directive';
import { RevalidateOnDirective } from '../../../../core/directives/revalidate-on.directive';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

export const NODE_MIME_TYPE = 'text/plain';

const DEF_NODE: NodeAttachDto = {
  name: '',
  sourceMode: MinionMode.NODE,
  remote: {
    uri: '',
    authPack: '',
  },
};

const ACTION_MIGRATE: BdDialogMessageAction<boolean> = {
  name: 'Migrate',
  result: true,
  confirm: true,
};

@Component({
    selector: 'app-add-node',
    templateUrl: './add-node.component.html',
    styleUrls: ['./add-node.component.css'],
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, BdFileDropComponent, BdFormInputComponent, IdentifierValidator, EditUniqueValueValidatorDirective, RevalidateOnDirective, BdButtonComponent, AsyncPipe]
})
export class AddNodeComponent implements DirtyableDialog, OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  private readonly actions = inject(ActionsService);
  private readonly snackbar = inject(MatSnackBar);
  protected readonly nodesAdmin = inject(NodesAdminService);

  private readonly adding$ = new BehaviorSubject<boolean>(false);

  protected nodeName$ = new BehaviorSubject<string>(null);
  protected nodeNames$ = this.nodesAdmin.nodes$.pipe(
    skipWhile((n) => !n?.length),
    map((n) => n.map((x) => x.name)),
    tap(() => setTimeout(() => this.form?.controls['name'].updateValueAndValidity())),
    startWith([]),
  );
  protected mappedAdd$ = this.actions.action(
    [Actions.ADD_NODE, Actions.CONVERT_TO_NODE],
    this.adding$,
    null,
    null,
    // the dummy string is to not react on *any* node beind manipulated from remote events until the user starts typing.
    this.nodeName$.pipe(map((n) => (!n?.length ? '__DUMMY__' : n))),
  );
  protected data = cloneDeep(DEF_NODE);

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

  protected updateName(event: any) {
    this.data.name = event;
    this.nodeName$.next(event);
  }

  public isDirty(): boolean {
    return isDirty(this.data, DEF_NODE);
  }

  public canSave(): boolean {
    return !this.form?.invalid;
  }

  public doSave(): Observable<unknown> {
    this.adding$.next(true);
    return this.nodesAdmin.addNode(this.data).pipe(
      finalize(() => {
        this.adding$.next(false);
        this.nodeName$.next(null);
      }),
    );
  }

  protected onSave() {
    let confirmation: Observable<boolean> = of(true);
    if (this.data.sourceMode && this.data.sourceMode !== MinionMode.NODE) {
      confirmation = this.dialog.message({
        header: 'Migrate Server',
        message: `This action will migrate the selected server from mode <code>${this.data.sourceMode}</code> to <code>NODE</code>. Existing software will be moved to this server. The migrated server will no longer provide any user interface. This action cannot be undone. Are you sure?`,
        icon: 'warning',
        actions: [ACTION_CANCEL, ACTION_MIGRATE],
      });
    }

    confirmation.subscribe((x) => {
      if (x) {
        this.doSave().subscribe(() => {
          this.data = cloneDeep(DEF_NODE);
          this.tb.closePanel();
        });
      }
    });
  }

  private readFile(file: File) {
    const reader = new FileReader();
    reader.onload = () => {
      this.setData(JSON.parse(reader.result.toString()));
    };
    reader.readAsText(file);
  }

  protected onDrop(event: DragEvent) {
    event.preventDefault();

    if (event.dataTransfer.types.includes(NODE_MIME_TYPE)) {
      this.setData(JSON.parse(event.dataTransfer.getData(NODE_MIME_TYPE)));
    }
  }

  private setData(data: NodeAttachDto) {
    if (!data?.remote?.authPack?.length || !data?.remote?.uri?.length || !data?.name?.length) {
      this.snackbar.open('Invalid Data - Make sure to drag the correct card.', 'DISMISS', { duration: 10000 });
      return;
    }

    this.data = data;
  }

  protected onOver(event: DragEvent) {
    // need to cancel the event and return false to ALLOW drop.
    if (event.preventDefault) {
      event.preventDefault();
    }

    return false;
  }

  protected fileAdded(event: any) {
    this.readFile(event);
  }
}
