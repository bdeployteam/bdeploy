import { Component, Input, OnChanges, OnInit, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { NavAreasService } from '../../services/nav-areas.service';
import { BdPanelButtonComponent } from '../bd-panel-button/bd-panel-button.component';

@Component({
  selector: 'app-bd-dialog-toolbar',
  templateUrl: './bd-dialog-toolbar.component.html',
  styleUrls: ['./bd-dialog-toolbar.component.css'],
})
export class BdDialogToolbarComponent implements OnInit, OnChanges {
  @Input() header: string;
  @Input() panel = false;
  @Input() route: any[];
  @Input() relative = true;
  @Input() actionText = 'Back to Overview';
  @Input() actionIcon = 'arrow_back';
  @Input() actionCollapsed = true;

  @ViewChild('backButton', { static: false }) back: BdPanelButtonComponent;

  constructor(private title: Title, private areas: NavAreasService) {}

  ngOnInit(): void {
    this.ngOnChanges();
  }

  ngOnChanges(): void {
    if (!this.panel) {
      this.title.setTitle(`BDeploy - ${this.header}`);
    }
  }

  public closePanel() {
    if (!!this.route) {
      this.back.onClick();
    } else {
      this.areas.closePanel();
    }
  }
}
