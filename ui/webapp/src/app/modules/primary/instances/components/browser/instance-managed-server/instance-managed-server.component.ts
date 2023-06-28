import { Component, Input } from '@angular/core';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';

@Component({
  selector: 'app-instance-managed-server',
  templateUrl: './instance-managed-server.component.html',
  styleUrls: ['./instance-managed-server.component.css'],
})
export class InstanceManagedServerComponent {
  @Input() record: InstanceDto;

  constructor(
    public auth: AuthenticationService,
    private areas: NavAreasService
  ) {}

  /* template */ goToServerPage() {
    this.areas.navigateBoth(
      ['/servers', 'browser', this.areas.groupContext$.value],
      ['panels', 'servers', 'details', this.record.managedServer.hostName]
    );
  }
}
