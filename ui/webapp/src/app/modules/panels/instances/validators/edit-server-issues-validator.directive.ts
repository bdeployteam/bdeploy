import { Directive, Input, inject } from '@angular/core';
import { AsyncValidator, NG_ASYNC_VALIDATORS, ValidationErrors } from '@angular/forms';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  BdValidationMessageExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProcessEditService } from '../services/process-edit.service';

@Directive({
  selector: '[appServerIssuesValidator]',
  providers: [
    {
      provide: NG_ASYNC_VALIDATORS,
      useExisting: EditServerIssuesValidatorDirective,
      multi: true,
    },
  ],
})
export class EditServerIssuesValidatorDirective implements AsyncValidator, BdValidationMessageExtractor {
  private edit = inject(ProcessEditService);
  private instanceEdit = inject(InstanceEditService);

  public readonly id = 'edit-server-issue';

  @Input() appServerIssuesValidator: string;

  constructor() {
    bdValidationRegisterMessageExtractor(this);
  }

  public extract(label: string, errors: ValidationErrors): string {
    if (errors[this.id]) {
      return errors[this.id];
    }
  }

  public validate(): Observable<ValidationErrors | null> {
    return this.instanceEdit.requestValidation().pipe(
      map((iu) => {
        const validation = iu?.filter(
          (v) => v.appId === this.edit.process$.value.id && v.paramId === this.appServerIssuesValidator
        );
        if (!validation?.length) {
          return null;
        }

        const errors = {};
        errors[this.id] = validation[0].message; // we can only show one message per parameter.
        return errors;
      })
    );
  }
}
