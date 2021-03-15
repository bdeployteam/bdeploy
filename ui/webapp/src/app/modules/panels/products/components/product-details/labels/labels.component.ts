import { Component, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { ProductDto } from 'src/app/models/gen.dtos';
import { ProductDetailsService } from '../../../services/product-details.service';

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
  selector: 'app-labels',
  templateUrl: './labels.component.html',
  styleUrls: ['./labels.component.css'],
  providers: [ProductDetailsService],
})
export class LabelsComponent implements OnInit {
  constructor(public singleProduct: ProductDetailsService) {}

  /* template */ columns: BdDataColumn<LabelRecord>[] = [labelKeyColumn, labelValueColumn];

  ngOnInit(): void {}

  /* template */ mapLabels(prod: ProductDto) {
    const labels: LabelRecord[] = [];
    for (const k of Object.keys(prod.labels)) {
      labels.push({ key: k, value: prod.labels[k] });
    }
    return labels;
  }
}
