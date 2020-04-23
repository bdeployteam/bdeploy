import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormControl, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { cloneDeep, isEqual } from 'lodash';
import { Observable, of } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { EMPTY_SOFTWARE_REPO } from '../../../../models/consts';
import { SoftwareRepositoryConfiguration } from '../../../../models/gen.dtos';
import { ErrorMessage, Logger, LoggingService } from '../../../core/services/logging.service';
import { MessageBoxMode } from '../../../shared/components/messagebox/messagebox.component';
import { MessageboxService } from '../../../shared/services/messagebox.service';
import { InstanceGroupValidators } from '../../../shared/validators/instance-group.validator';
import { SoftwareRepositoryService } from '../../services/software-repository.service';

@Component({
  selector: 'app-software-repo-add-edit',
  templateUrl: './software-repo-add-edit.component.html',
  styleUrls: ['./software-repo-add-edit.component.css'],
})
export class SoftwareRepoAddEditComponent implements OnInit {
  log: Logger = this.loggingService.getLogger('SoftwareRepoAddEditComponent');

  nameParam: string;

  public loading = false;

  public clonedSoftwareRepo: SoftwareRepositoryConfiguration;

  public softwareRepoFormGroup = this.fb.group({
    name: ['', [Validators.required, InstanceGroupValidators.namePattern]], // re-use validator pattern
    description: ['', Validators.required]
  });

  get nameControl() {
    return this.softwareRepoFormGroup.get('name');
  }
  get descriptionControl() {
    return this.softwareRepoFormGroup.get('description');
  }

  constructor(
    private fb: FormBuilder,
    private softwareRepoService: SoftwareRepositoryService,
    private route: ActivatedRoute,
    private loggingService: LoggingService,
    private messageBoxService: MessageboxService,
    public location: Location,
    private router: Router
  ) {}

  ngOnInit() {
    this.nameParam = this.route.snapshot.paramMap.get('name');
    this.log.debug('nameParam = ' + this.nameParam);

    if (this.isCreate()) {
      const softwareRepo = cloneDeep(EMPTY_SOFTWARE_REPO);
      this.softwareRepoFormGroup.setValue(softwareRepo);
      this.clonedSoftwareRepo = cloneDeep(softwareRepo);
    } else {
      this.softwareRepoService.getSoftwareRepository(this.nameParam).subscribe(
        softwareRepo => {
          this.log.debug('got software repository ' + this.nameParam);
          this.softwareRepoFormGroup.setValue(softwareRepo);
          this.clonedSoftwareRepo = cloneDeep(softwareRepo);
        },
        error => {
          this.log.errorWithGuiMessage(new ErrorMessage('reading software repository failed', error));
        },
      );
    }
  }

  public getErrorMessage(ctrl: FormControl): string {
    if (ctrl.hasError('required')) {
      return 'Required';
    } else if (ctrl.hasError('namePattern')) {
      return 'Must be a single word starting with a letter or digit, followed by valid characters: A-Z a-z 0-9 _ - .';
    }
    return 'Unknown error';
  }

  public isCreate(): boolean {
    return this.nameParam == null;
  }

  onSubmit(): void {
    this.loading = true;

    const softwareRepo = this.softwareRepoFormGroup.getRawValue();

    if (this.isCreate()) {
      this.softwareRepoService
        .createSoftwareRepository(softwareRepo)
        .pipe(
          finalize(() => {
            this.loading = false;
          }),
        )
        .subscribe(result => {
          this.log.info('created new software repository ' + softwareRepo.name);
          this.clonedSoftwareRepo = softwareRepo;
          this.router.navigate(['/softwarerepo/browser']);
        });
    } else {
      this.softwareRepoService
        .updateSoftwareRepository(this.nameParam, softwareRepo)
        .pipe(
          finalize(() => {
            this.loading = false;
          }),
        )
        .subscribe(result => {
          this.log.info('updated software repository ' + this.nameParam);
          this.clonedSoftwareRepo = softwareRepo;
          this.router.navigate(['/softwarerepo/browser']);
        });
    }
  }

  isModified(): boolean {
    const softwareRepo: SoftwareRepositoryConfiguration = this.softwareRepoFormGroup.getRawValue();
    return !isEqual(softwareRepo, this.clonedSoftwareRepo);
  }

  canDeactivate(): Observable<boolean> {
    if (!this.isModified()) {
      return of(true);
    }
    return this.messageBoxService.open({
      title: 'Unsaved changes',
      message: 'Software Repository was modified. Close without saving?',
      mode: MessageBoxMode.CONFIRM_WARNING,
    });
  }

}
