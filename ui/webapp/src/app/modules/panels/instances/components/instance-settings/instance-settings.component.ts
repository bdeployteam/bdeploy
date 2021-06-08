import { Component, OnInit } from '@angular/core';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

@Component({
  selector: 'app-instance-settings',
  templateUrl: './instance-settings.component.html',
  styleUrls: ['./instance-settings.component.css'],
})
export class InstanceSettingsComponent implements OnInit {
  constructor(public auth: AuthenticationService, public edit: InstanceEditService) {}

  ngOnInit(): void {}
}
