import { Component, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, finalize } from 'rxjs';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';

@Component({
  selector: 'app-add-repository',
  templateUrl: './add-repository.component.html',
  styleUrls: ['./add-repository.component.css'],
})
export class AddRepositoryComponent implements OnInit {
  /* template */ saving$ = new BehaviorSubject<boolean>(false);
  /* template */ repository: Partial<SoftwareRepositoryConfiguration> = {};

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  constructor(private repositories: RepositoriesService, private areas: NavAreasService) {}

  ngOnInit(): void {}

  /* template */ onSave() {
    this.saving$.next(true);
    this.repositories
      .create(this.repository)
      .pipe(finalize(() => this.saving$.next(false)))
      .subscribe(
        (_) => {
          this.saving$.next(false);
          this.areas.closePanel();
        },
        () => {}
      );
  }
}
