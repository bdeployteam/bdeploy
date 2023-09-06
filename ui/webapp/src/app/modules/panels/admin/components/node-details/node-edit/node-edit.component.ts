import { Component, OnDestroy, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Observable, Subscription, combineLatest, finalize } from 'rxjs';
import { Actions, RemoteService } from 'src/app/models/gen.dtos';
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
export class NodeEditComponent implements OnDestroy, DirtyableDialog {
  private saving$ = new BehaviorSubject<boolean>(false);
  private actions = inject(ActionsService);

  protected nodeName$ = new BehaviorSubject<string>(null);
  protected mappedSave$ = this.actions.action(
    [Actions.EDIT_NODE, Actions.REPLACE_NODE],
    this.saving$,
    null,
    null,
    this.nodeName$
  );

  /* template */ data: RemoteService;
  /* template */ orig: RemoteService;
  /* template */ replace = false;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild(NgForm) private form: NgForm;

  private subscription: Subscription;

  constructor(public nodesAdmin: NodesAdminService, areas: NavAreasService) {
    this.subscription = areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      combineLatest([areas.panelRoute$, nodesAdmin.nodes$]).subscribe(([r, n]) => {
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
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  isDirty(): boolean {
    return isDirty(this.data, this.orig);
  }

  canSave(): boolean {
    return !this.form?.invalid;
  }

  doSave(): Observable<any> {
    this.saving$.next(true);
    if (this.replace) {
      return this.nodesAdmin
        .replaceNode(this.nodeName$.value, this.data)
        .pipe(finalize(() => this.saving$.next(false)));
    } else {
      return this.nodesAdmin.editNode(this.nodeName$.value, this.data).pipe(finalize(() => this.saving$.next(false)));
    }
  }

  onSave() {
    this.doSave().subscribe(() => {
      this.data = this.orig; // avoid "unsaved" warning.
      this.tb.closePanel();
    });
  }

  private readFile(file: File) {
    const reader = new FileReader();
    reader.onload = () => {
      const x = JSON.parse(reader.result.toString());
      this.data.uri = x.remote.uri;
      this.data.authPack = x.remote.authPack;
    };
    reader.readAsText(file);
  }

  /* template */ onDrop(event: DragEvent) {
    event.preventDefault();

    if (event.dataTransfer.files.length > 0) {
      this.readFile(event.dataTransfer.files[0]);
    } else if (event.dataTransfer.types.includes(NODE_MIME_TYPE)) {
      const x = JSON.parse(event.dataTransfer.getData(NODE_MIME_TYPE));
      this.data.uri = x.remote.uri;
      this.data.authPack = x.remote.authPack;
    }
  }

  /* template */ onOver(event: DragEvent) {
    // need to cancel the event and return false to ALLOW drop.
    if (event.preventDefault) {
      event.preventDefault();
    }

    return false;
  }
}
