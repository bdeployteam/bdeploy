import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { HistoryEntryVersionDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-instance-history-compare',
  templateUrl: './instance-history-compare.component.html',
  styleUrls: ['./instance-history-compare.component.css'],
})
export class InstanceHistoryCompareComponent implements OnInit {
  promise: Observable<HistoryEntryVersionDto>;
  result: HistoryEntryVersionDto;
  title: string;

  loading = false;

  constructor(@Inject(MAT_DIALOG_DATA) public data) {
    this.promise = data[0];
    this.title = data[1];
  }

  ngOnInit() {
    this.loading = true;
    this.promise.pipe(finalize(() => (this.loading = false))).subscribe((ret) => {
      this.result = ret;
    });
  }
}
