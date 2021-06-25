import { Component, OnInit } from '@angular/core';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

@Component({
  selector: 'app-ports',
  templateUrl: './ports.component.html',
  styleUrls: ['./ports.component.css'],
})
export class PortsComponent implements OnInit {
  constructor(public edit: InstanceEditService) {}

  ngOnInit(): void {}
}
