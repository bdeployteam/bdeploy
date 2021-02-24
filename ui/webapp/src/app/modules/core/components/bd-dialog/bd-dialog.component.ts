import { Component, Input, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'app-bd-dialog',
  templateUrl: './bd-dialog.component.html',
  styleUrls: ['./bd-dialog.component.css'],
})
export class BdDialogComponent implements OnInit {
  @Input() loadingWhen$: BehaviorSubject<boolean>;

  constructor() {}

  ngOnInit(): void {}
}
