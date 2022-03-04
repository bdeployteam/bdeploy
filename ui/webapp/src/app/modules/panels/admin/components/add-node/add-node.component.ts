import { Component, OnDestroy, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, finalize, Observable, Subscription } from 'rxjs';
import { RemoteService } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { NodesAdminService } from 'src/app/modules/primary/admin/services/nodes-admin.service';

export const NODE_MIME_TYPE = 'text/plain';

interface SimpleNodeConfig {
  name: string;
  remote: RemoteService;
}

const DEF_NODE: SimpleNodeConfig = {
  name: '',
  remote: {
    uri: '',
    authPack: '',
  },
};

@Component({
  selector: 'app-add-node',
  templateUrl: './add-node.component.html',
  styleUrls: ['./add-node.component.css'],
})
export class AddNodeComponent implements DirtyableDialog, OnDestroy {
  /* template */ adding$ = new BehaviorSubject<boolean>(false);
  /* template */ data = cloneDeep(DEF_NODE);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild(NgForm) private form: NgForm;

  private subscription: Subscription;

  constructor(public nodesAdmin: NodesAdminService, areas: NavAreasService) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  isDirty(): boolean {
    return isDirty(this.data, DEF_NODE);
  }

  canSave(): boolean {
    return !this.form?.invalid;
  }

  doSave(): Observable<any> {
    this.adding$.next(true);
    return this.nodesAdmin
      .addNode(this.data.name, this.data.remote)
      .pipe(finalize(() => this.adding$.next(false)));
  }

  onSave() {
    this.doSave().subscribe(() => {
      this.data = cloneDeep(DEF_NODE);
      this.tb.closePanel();
    });
  }

  private readFile(file: File) {
    const reader = new FileReader();
    reader.onload = () => {
      this.data = JSON.parse(reader.result.toString());
    };
    reader.readAsText(file);
  }

  /* template */ onDrop(event: DragEvent) {
    event.preventDefault();

    if (event.dataTransfer.files.length > 0) {
      this.readFile(event.dataTransfer.files[0]);
    } else if (event.dataTransfer.types.includes(NODE_MIME_TYPE)) {
      this.data = JSON.parse(event.dataTransfer.getData(NODE_MIME_TYPE));
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
