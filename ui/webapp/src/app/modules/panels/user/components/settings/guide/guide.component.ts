import { Component, OnInit, ViewChild } from '@angular/core';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { GuideService, GuideType } from 'src/app/modules/core/services/guide.service';

@Component({
  selector: 'app-guide',
  templateUrl: './guide.component.html',
  styleUrls: ['./guide.component.css'],
})
export class GuideComponent implements OnInit {
  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  constructor(public guides: GuideService) {}

  ngOnInit(): void {}

  enableUser(): void {
    this.guides.saveType(GuideType.USER);
    this.promptReload();
  }

  enableDeveloper(): void {
    this.guides.saveType(GuideType.DEVELOPER);
    this.promptReload();
  }

  promptReload(): void {
    this.dialog.confirm('Reload', 'Changes to guides will take effect after a reload. Reload now?', 'refresh').subscribe((b) => {
      if (b) {
        window.location.reload();
      }
    });
  }
}
