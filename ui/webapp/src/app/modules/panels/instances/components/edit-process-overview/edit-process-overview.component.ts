import { Component, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ProcessEditService } from '../../services/process-edit.service';

@Component({
  selector: 'app-edit-process-overview',
  templateUrl: './edit-process-overview.component.html',
})
export class EditProcessOverviewComponent {
  private readonly areas = inject(NavAreasService);
  private readonly snackbar = inject(MatSnackBar);
  protected edit = inject(ProcessEditService);
  protected instanceEdit = inject(InstanceEditService);
  protected servers = inject(ServersService);

  protected clientNodeName = CLIENT_NODE_NAME;

  protected doDelete() {
    const process = this.edit.process$.value;
    this.edit.removeProcess();
    this.instanceEdit.conceal(`Remove ${process.name}`);
    this.areas.closePanel();
  }

  protected doCopy() {
    const process = this.edit.process$.value;

    navigator.clipboard.writeText(JSON.stringify(process, null, '\t')).then(
      () =>
        this.snackbar.open('Copied to clipboard successfully', null, {
          duration: 1000,
        }),
      () =>
        this.snackbar.open('Unable to write to clipboard.', null, {
          duration: 1000,
        }),
    );
  }
}
