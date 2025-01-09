import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { BulkOperationResultDto } from 'src/app/models/gen.dtos';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'app-bd-bulk-operation-result',
    templateUrl: './bd-bulk-operation-result.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatIcon]
})
export class BdBulkOperationResultComponent {
  @Input() bulkOpResult: BulkOperationResultDto;
}
