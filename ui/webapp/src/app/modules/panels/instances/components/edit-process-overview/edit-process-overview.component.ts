import { Component, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ProcessEditService } from '../../services/process-edit.service';

@Component({
  selector: 'app-edit-process-overview',
  templateUrl: './edit-process-overview.component.html',
  styleUrls: ['./edit-process-overview.component.css'],
})
export class EditProcessOverviewComponent implements OnInit {
  /* template */ clientNodeName = CLIENT_NODE_NAME;

  constructor(
    public edit: ProcessEditService,
    public instanceEdit: InstanceEditService,
    public servers: ServersService,
    private areas: NavAreasService,
    private snackbar: MatSnackBar
  ) {}

  ngOnInit(): void {}

  /* template */ doDelete() {
    const process = this.edit.process$.value;
    this.edit.removeProcess();
    this.instanceEdit.conceal(`Remove ${process.name}`);
    this.areas.closePanel();
  }

  /* template */ doCopy() {
    const process = this.edit.process$.value;

    navigator.clipboard.writeText(JSON.stringify(process, null, '\t')).then(
      () => this.snackbar.open('Copied to clipboard successfully', null, { duration: 1000 }),
      () => this.snackbar.open('Unable to write to clipboard.', null, { duration: 1000 })
    );
  }
}
