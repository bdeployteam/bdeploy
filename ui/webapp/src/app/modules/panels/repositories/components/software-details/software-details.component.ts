import { Component, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceUsageDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { RepositoryService } from 'src/app/modules/primary/repositories/services/repository.service';
import { SoftwareDetailsService } from '../../services/software-details.service';

const instanceNameColumn: BdDataColumn<InstanceUsageDto> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
};

const instanceTagColumn: BdDataColumn<InstanceUsageDto> = {
  id: 'tag',
  name: 'Ver.',
  data: (r) => r.tag,
  width: '30px',
};

@Component({
  selector: 'app-software-details',
  templateUrl: './software-details.component.html',
  styleUrls: ['./software-details.component.css'],
  providers: [SoftwareDetailsService],
})
export class SoftwareDetailsComponent implements OnInit {
  /* template */ deleting$ = new BehaviorSubject<boolean>(false);
  /* template */ columns: BdDataColumn<InstanceUsageDto>[] = [instanceNameColumn, instanceTagColumn];

  /* template */ loading$ = combineLatest([this.deleting$, this.repository.loading$]).pipe(map(([a, b]) => a || b));
  /* template */ preparing$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  constructor(
    public repository: RepositoryService,
    public detailsService: SoftwareDetailsService,
    public areas: NavAreasService,
    public auth: AuthenticationService
  ) {}

  ngOnInit(): void {}

  /* template */ doDelete(software: any) {
    this.dialog.confirm(`Delete ${software.key.tag}`, `Are you sure you want to delete version ${software.key.tag}?`, 'delete').subscribe((r) => {
      if (r) {
        this.deleting$.next(true);
        this.detailsService
          .delete()
          .pipe(finalize(() => this.deleting$.next(false)))
          .subscribe((_) => {
            this.areas.closePanel();
          });
      }
    });
  }

  /* template */ doDownload() {
    this.preparing$.next(true);
    this.detailsService
      .download()
      .pipe(finalize(() => this.preparing$.next(false)))
      .subscribe();
  }
}
