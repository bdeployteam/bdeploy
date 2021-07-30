import { Component, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { SoftwareDetailsService } from '../../../services/software-details.service';

interface LabelRecord {
  key: string;
  value: string;
}

const labelKeyColumn: BdDataColumn<LabelRecord> = {
  id: 'key',
  name: 'Label',
  data: (r) => r.key,
  width: '90px',
};

const labelValueColumn: BdDataColumn<LabelRecord> = {
  id: 'value',
  name: 'Value',
  data: (r) => r.value,
  width: '190px',
};

@Component({
  selector: 'app-software-labels',
  templateUrl: './labels.component.html',
  styleUrls: ['./labels.component.css'],
  providers: [SoftwareDetailsService],
})
export class LabelsComponent implements OnInit {
  constructor(public details: SoftwareDetailsService) {}

  /* template */ columns: BdDataColumn<LabelRecord>[] = [labelKeyColumn, labelValueColumn];

  ngOnInit(): void {}

  /* template */ mapLabels(software: any) {
    const labels: LabelRecord[] = [];
    if (!!software.labels) {
      for (const k of Object.keys(software.labels)) {
        labels.push({ key: k, value: software.labels[k] });
      }
    }
    return labels;
  }
}
