import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { OperatingSystem } from '../models/gen.dtos';
import { SoftwareUpdateService } from '../services/software-update.service';
import { GroupedKeys } from '../update-browser/update-browser.component';

@Component({
  selector: 'app-update-card',
  templateUrl: './update-card.component.html',
  styleUrls: ['./update-card.component.css']
})
export class UpdateCardComponent implements OnInit {

  @Input() public version: GroupedKeys;
  @Input() public allowUpdate = true;
  @Output() public update = new EventEmitter<GroupedKeys>();
  @Output() public delete = new EventEmitter<GroupedKeys>();

  constructor(private updService: SoftwareUpdateService) { }

  ngOnInit() {
  }

  getDownload(os: OperatingSystem) {
    // find actual key for os.
    for (const k of this.version.keys) {
      if (k.name.includes(os.toLowerCase())) {
        window.location.href = this.updService.getDownloadUrl(k);
        return;
      }
    }

    // should never happen
  }

}
