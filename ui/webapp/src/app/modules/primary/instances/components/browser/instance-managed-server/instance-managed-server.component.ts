import { Component, Input, inject } from '@angular/core';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { MatCard } from '@angular/material/card';
import { MatIcon } from '@angular/material/icon';
import { BdPopupDirective } from '../../../../../core/components/bd-popup/bd-popup.directive';
import { AsyncPipe, DatePipe } from '@angular/common';

@Component({
    selector: 'app-instance-managed-server',
    templateUrl: './instance-managed-server.component.html',
    styleUrls: ['./instance-managed-server.component.css'],
    imports: [MatCard, MatIcon, BdPopupDirective, AsyncPipe, DatePipe]
})
export class InstanceManagedServerComponent {
  private readonly areas = inject(NavAreasService);
  protected readonly auth = inject(AuthenticationService);

  @Input() record: InstanceDto;

  protected goToServerPage() {
    this.areas.navigateBoth(
      ['/servers', 'browser', this.areas.groupContext$.value],
      ['panels', 'servers', 'details', this.record.managedServer.hostName],
    );
  }
}
