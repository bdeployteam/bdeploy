import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { HistoryEntryVersionDto } from 'src/app/models/gen.dtos';
import { InstanceService } from '../../services/instance.service';

@Component({
  selector: 'app-instance-history-compare',
  templateUrl: './instance-history-compare.component.html',
  styleUrls: ['./instance-history-compare.component.css'],
})
export class InstanceHistoryCompareComponent implements OnInit {
  instanceService: InstanceService;
  instanceGroup: string;
  instanceId: string;
  compareVersionA: string;
  compareVersionB: string;

  result: HistoryEntryVersionDto;

  constructor(@Inject(MAT_DIALOG_DATA) public data) {
    this.instanceService = data[0];
    this.instanceGroup = data[1];
    this.instanceId = data[2];
    this.compareVersionA = data[3][0];
    this.compareVersionB = data[3][1];
  }

  ngOnInit(): void {
    this.instanceService
      .getVersionComparison(
        this.instanceGroup,
        this.instanceId,
        this.compareVersionA,
        this.compareVersionB
      )
      .subscribe((ret) => {
        this.result = ret;
      });
  }
}
