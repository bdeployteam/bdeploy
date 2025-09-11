import { Component, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ProcessEditService } from '../../services/process-edit.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdNotificationCardComponent } from '../../../../core/components/bd-notification-card/bd-notification-card.component';
import { AsyncPipe } from '@angular/common';
import { NodeType } from 'src/app/models/gen.dtos';

@Component({
    selector: 'app-edit-process-overview',
    templateUrl: './edit-process-overview.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdPanelButtonComponent, BdButtonComponent, BdNotificationCardComponent, AsyncPipe]
})
export class EditProcessOverviewComponent {
  private readonly areas = inject(NavAreasService);
  private readonly snackbar = inject(MatSnackBar);
  protected readonly edit = inject(ProcessEditService);
  protected readonly instanceEdit = inject(InstanceEditService);
  protected readonly servers = inject(ServersService);

  protected clientNodeType = NodeType.CLIENT;

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
