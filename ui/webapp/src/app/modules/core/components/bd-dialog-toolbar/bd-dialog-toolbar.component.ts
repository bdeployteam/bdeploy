import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';

@Component({
  selector: 'app-bd-dialog-toolbar',
  templateUrl: './bd-dialog-toolbar.component.html',
  styleUrls: ['./bd-dialog-toolbar.component.css'],
})
export class BdDialogToolbarComponent implements OnInit, OnChanges {
  @Input() header: string;
  @Input() closeablePanel = false;

  constructor(private title: Title) {}

  ngOnInit(): void {
    this.ngOnChanges();
  }

  ngOnChanges(): void {
    if (!this.closeablePanel) {
      this.title.setTitle(`BDeploy - ${this.header}`);
    }
  }
}
