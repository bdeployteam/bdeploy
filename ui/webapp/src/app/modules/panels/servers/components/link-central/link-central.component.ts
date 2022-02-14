import { Component, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ManagedMasterDto, ObjectChangeType } from 'src/app/models/gen.dtos';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  EMPTY_SCOPE,
  ObjectChangesService,
} from 'src/app/modules/core/services/object-changes.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ATTACH_MIME_TYPE } from '../../services/server-details.service';

@Component({
  selector: 'app-link-central',
  templateUrl: './link-central.component.html',
  styleUrls: ['./link-central.component.css'],
})
export class LinkCentralComponent implements OnInit {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ payload: ManagedMasterDto;

  private subscription: Subscription;

  constructor(
    private servers: ServersService,
    private changes: ObjectChangesService,
    private areas: NavAreasService,
    public downloads: DownloadService
  ) {}

  ngOnInit(): void {
    this.servers
      .getManagedIdent()
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((r) => (this.payload = r));

    this.subscription = this.changes.subscribe(
      ObjectChangeType.MANAGED_MASTER_ATTACH,
      EMPTY_SCOPE,
      () => this.areas.closePanel()
    );
  }

  /* template */ onDragStart($event) {
    $event.dataTransfer.effectAllowed = 'link';
    $event.dataTransfer.setData(ATTACH_MIME_TYPE, JSON.stringify(this.payload));
  }

  /* template */ onDrop(event: DragEvent) {
    event.preventDefault();

    if (event.dataTransfer.files.length > 0) {
      const reader = new FileReader();
      reader.onload = () => {
        const data = reader.result.toString();
        this.onManualAttach(data);
      };
      reader.readAsText(event.dataTransfer.files[0]);
    } else if (event.dataTransfer.types.includes(ATTACH_MIME_TYPE)) {
      const data = event.dataTransfer.getData(ATTACH_MIME_TYPE);
      this.onManualAttach(data);
    }
  }

  /* template */ onOver(event: DragEvent) {
    // need to cancel the event and return false to ALLOW drop.
    if (event.preventDefault) {
      event.preventDefault();
    }

    return false;
  }

  private onManualAttach(ident: string) {
    this.servers.manualAttachCentral(ident).subscribe(() => {
      this.areas.closePanel();
    });
  }
}
