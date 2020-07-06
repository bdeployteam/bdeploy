import { Component, Inject, OnInit } from '@angular/core';
import { MAT_BOTTOM_SHEET_DATA } from '@angular/material/bottom-sheet';
import { finalize } from 'rxjs/operators';
import { InstanceService } from '../../services/instance.service';

interface PortSheetData {
  instanceGroup: string;
  instanceId: string;
  minionName: string;
  appName: string;
  ports: number[];
  labels: string[];
}

@Component({
  selector: 'app-process-port-list',
  templateUrl: './process-port-list.component.html',
  styleUrls: ['./process-port-list.component.css']
})
export class ProcessPortListComponent implements OnInit {

  loading = true;
  states: { [key: number]: boolean; };

  constructor(@Inject(MAT_BOTTOM_SHEET_DATA) public data: PortSheetData, private instanceService: InstanceService) { }

  ngOnInit(): void {
    this.instanceService.getOpenPorts(this.data.instanceGroup, this.data.instanceId, this.data.minionName, this.data.ports).pipe(finalize(() => this.loading = false)).subscribe(r => {
      this.states = r;
    });
  }

}
