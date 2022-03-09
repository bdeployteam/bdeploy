import { Component, OnDestroy, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import {
  BehaviorSubject,
  combineLatest,
  finalize,
  Observable,
  Subscription,
} from 'rxjs';
import { RemoteService } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { NodesAdminService } from 'src/app/modules/primary/admin/services/nodes-admin.service';

export const NODE_MIME_TYPE = 'text/plain';

@Component({
  selector: 'app-node-edit',
  templateUrl: './node-edit.component.html',
  styleUrls: ['./node-edit.component.css'],
})
export class NodeEditComponent implements OnDestroy, DirtyableDialog {
  /* template */ saving$ = new BehaviorSubject<boolean>(false);
  /* template */ data: RemoteService;
  /* template */ orig: RemoteService;
  /* template */ nodeName: string;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild(NgForm) private form: NgForm;

  private subscription: Subscription;

  constructor(public nodesAdmin: NodesAdminService, areas: NavAreasService) {
    this.subscription = areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      combineLatest([areas.panelRoute$, nodesAdmin.nodes$]).subscribe(
        ([r, n]) => {
          if (!r?.params?.node || !n?.length) {
            this.nodeName = null;
            this.data = null;
            return;
          }

          this.nodeName = r.params.node;
          this.orig = n.find(
            (x) => x.name === this.nodeName
          ).status?.config?.remote;
          this.data = cloneDeep(this.orig);
        }
      )
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
    return this.nodesAdmin
      .editNode(this.nodeName, this.data)
      .pipe(finalize(() => this.saving$.next(false)));
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
