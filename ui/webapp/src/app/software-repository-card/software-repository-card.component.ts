import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { MessageBoxMode } from '../messagebox/messagebox.component';
import { SoftwareRepositoryConfiguration } from '../models/gen.dtos';
import { MessageboxService } from '../services/messagebox.service';
import { SoftwareRepositoryService } from '../services/software-repository.service';

@Component({
  selector: 'app-software-repository-card',
  templateUrl: './software-repository-card.component.html',
  styleUrls: ['./software-repository-card.component.css']
})
export class SoftwareRepositoryCardComponent implements OnInit {

  @Input() repository: SoftwareRepositoryConfiguration = null;
  @Output() removeEvent = new EventEmitter<boolean>();

  constructor(private repoService: SoftwareRepositoryService, private mbService: MessageboxService) { }

  ngOnInit() {
  }

  delete(): void {
    this.mbService
      .open({
        title: 'Delete Software Repository: ' + this.repository.name,
        message: 'Deleting a Software Repository <b>cannot be undone</b>.',
        mode: MessageBoxMode.CONFIRM_WARNING
      })
      .subscribe(result => {
        if (result !== true) {
          return;
        }
        this.repoService
          .deleteSoftwareRepository(this.repository.name)
          .subscribe(
            r => {
              this.removeEvent.emit(true);
            }
          );
      });
  }

}
