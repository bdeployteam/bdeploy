import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { SoftwareRepositoryConfiguration } from '../../../../../models/gen.dtos';
import { MessageBoxMode } from '../../../../core/components/messagebox/messagebox.component';
import { MessageboxService } from '../../../../core/services/messagebox.service';
import { SoftwareRepositoryService } from '../../services/software-repository.service';

@Component({
  selector: 'app-software-repository-card',
  templateUrl: './software-repository-card.component.html',
  styleUrls: ['./software-repository-card.component.css'],
})
export class SoftwareRepositoryCardComponent implements OnInit {
  @Input() repository: SoftwareRepositoryConfiguration = null;
  @Output() removeEvent = new EventEmitter<boolean>();

  constructor(
    private repoService: SoftwareRepositoryService,
    private mbService: MessageboxService,
    private authService: AuthenticationService
  ) {}

  ngOnInit() {}

  delete(): void {
    this.mbService
      .open({
        title: 'Delete Software Repository: ' + this.repository.name,
        message: 'Deleting a Software Repository <strong>cannot be undone</strong>.',
        mode: MessageBoxMode.CONFIRM_WARNING,
      })
      .subscribe((result) => {
        if (result !== true) {
          return;
        }
        this.repoService.deleteSoftwareRepository(this.repository.name).subscribe((r) => {
          this.removeEvent.emit(true);
        });
      });
  }

  public isReadOnly(): boolean {
    return !this.authService.isGlobalAdmin();
  }
}
