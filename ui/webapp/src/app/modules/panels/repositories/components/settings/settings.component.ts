import { Component, ViewChild, inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { RepositoryDetailsService } from '../../services/repository-details.service';

@Component({
    selector: 'app-settings',
    templateUrl: './settings.component.html',
    standalone: false
})
export class SettingsComponent {
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthenticationService);
  protected readonly repositories = inject(RepositoriesService);
  protected readonly details = inject(RepositoryDetailsService);

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  protected deleting$ = new BehaviorSubject<boolean>(false);

  protected onDelete(repository: SoftwareRepositoryConfiguration): void {
    this.dialog
      .confirm(
        `Delete ${repository.name}`,
        `Are you sure you want to delete the software repository <strong>${repository.name}</strong>? ` +
          `This will permanently delete all products and external software packages that are stored in this repository.`,
        'delete',
        repository.name,
        `Confirm using Software Repository Name`,
      )
      .subscribe((r) => {
        if (r) {
          this.deleting$.next(true);
          this.details
            .delete(repository)
            .pipe(finalize(() => this.deleting$.next(false)))
            .subscribe(() => {
              this.router.navigate(['repositories', 'browser']);
            });
        }
      });
  }
}
