import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Observable, Subscription, combineLatest, finalize } from 'rxjs';
import { Actions, NodeAttachDto, RemoteService } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { NodesAdminService } from 'src/app/modules/primary/admin/services/nodes-admin.service';
import { NODE_MIME_TYPE } from '../../add-node/add-node.component';

@Component({
  selector: 'app-node-edit',
  templateUrl: './node-edit.component.html',
  styleUrls: ['./node-edit.component.css'],
})
export class NodeEditComponent implements OnInit, OnDestroy, DirtyableDialog {
  private readonly areas = inject(NavAreasService);
  private readonly actions = inject(ActionsService);
  private readonly snackbar = inject(MatSnackBar);
  protected readonly nodesAdmin = inject(NodesAdminService);

  private readonly saving$ = new BehaviorSubject<boolean>(false);

  protected nodeName$ = new BehaviorSubject<string>(null);
  protected mappedSave$ = this.actions.action(
    [Actions.EDIT_NODE, Actions.REPLACE_NODE],
    this.saving$,
    null,
    null,
    this.nodeName$,
  );

  protected data: RemoteService;
  protected orig: RemoteService;
  protected replace = false;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;
  @ViewChild(NgForm) private readonly form: NgForm;

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      combineLatest([this.areas.panelRoute$, this.nodesAdmin.nodes$]).subscribe(([r, n]) => {
        if (!r?.params?.node || !n?.length) {
          this.nodeName$.next(null);
          this.data = null;
          return;
        }

        this.replace = !!r.data.replace;
        this.nodeName$.next(r.params.node);
        this.orig = cloneDeep(n.find((x) => x.name === r.params.node).status?.config?.remote);
        this.orig.authPack = ''; // clear existing pack, not relevant AT ALL.
        this.data = cloneDeep(this.orig);
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return isDirty(this.data, this.orig);
  }

  public canSave(): boolean {
    return !this.form?.invalid;
  }

  public doSave(): Observable<unknown> {
    this.saving$.next(true);
    if (this.replace) {
      return this.nodesAdmin
        .replaceNode(this.nodeName$.value, this.data)
        .pipe(finalize(() => this.saving$.next(false)));
    } else {
      return this.nodesAdmin.editNode(this.nodeName$.value, this.data).pipe(finalize(() => this.saving$.next(false)));
    }
  }

  protected onSave() {
    this.doSave().subscribe(() => {
      this.data = this.orig; // avoid "unsaved" warning.
      this.tb.closePanel();
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
    if (!data?.remote?.authPack?.length || !data?.remote?.uri?.length) {
      this.snackbar.open('Invalid Data - Make sure to drag the correct card.', 'DISMISS', { duration: 10000 });
      return;
    }

    this.data.authPack = data.remote.authPack;
    this.data.uri = data.remote.uri;
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
