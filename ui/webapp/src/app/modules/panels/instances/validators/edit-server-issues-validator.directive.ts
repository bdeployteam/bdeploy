import { Directive, Input, inject } from '@angular/core';
import { AsyncValidator, NG_ASYNC_VALIDATORS, ValidationErrors } from '@angular/forms';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  bdValidationIdExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProcessEditService } from '../services/process-edit.service';

const ID = 'edit-server-issue';
bdValidationRegisterMessageExtractor(bdValidationIdExtractor(ID));

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
export class EditServerIssuesValidatorDirective implements AsyncValidator {
  private readonly edit = inject(ProcessEditService);
  private readonly instanceEdit = inject(InstanceEditService);

  @Input() appServerIssuesValidator: string;

  public validate(): Observable<ValidationErrors | null> {
    return this.instanceEdit.requestValidation().pipe(
      map((iu) => {
        const validation = iu?.filter(
          (v) => v.appId === this.edit.process$.value.id && v.paramId === this.appServerIssuesValidator,
        );
        if (!validation?.length) {
          return null;
        }

        const errors = {};
        errors[ID] = validation[0].message; // we can only show one message per parameter.
        return errors;
      }),
    );
  }
}
