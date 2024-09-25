import { Component, inject, Input, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, filter, Subscription, switchMap, tap } from 'rxjs';
import {
  InstancePurpose,
  ReportDescriptor,
  ReportParameterDescriptor,
  ReportParameterType,
  ReportRequestDto,
} from 'src/app/models/gen.dtos';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';

export interface ReportInputChange {
  key: string;
  value: any;
}

@Component({
  selector: 'app-report-form-input',
  templateUrl: './report-form-input.component.html',
})
export class ReportFormInputComponent implements OnInit, OnDestroy {
  private readonly groups = inject(GroupsService);
  private readonly products = inject(ProductsService);
  protected readonly reports = inject(ReportsService);
  protected readonly ReportParameterType = ReportParameterType;

  @Input()
  protected param: ReportParameterDescriptor;
  @Input()
  protected report: ReportDescriptor;
  @Input()
  protected request: ReportRequestDto;
  @Input()
  private changed$: BehaviorSubject<ReportInputChange>;

  protected values = [];
  protected labels: string[];

  private subscription: Subscription;

  ngOnInit(): void {
    if (this.param.type === ReportParameterType.INSTANCE_PURPOSE) {
      this.values = [InstancePurpose.PRODUCTIVE, InstancePurpose.DEVELOPMENT, InstancePurpose.TEST];
    }
    if (this.param.type === ReportParameterType.INSTANCE_GROUP) {
      this.subscription = this.groups.groups$.subscribe((gs) => {
        this.values = gs.map((g) => g.instanceGroupConfiguration.name);
      });
    }
    if (
      this.param.type === ReportParameterType.PRODUCT_KEY &&
      this.report.parameters.find((p) => p.key === this.param.dependsOn).type === ReportParameterType.INSTANCE_GROUP
    ) {
      this.subscription = this.changed$
        .pipe(
          filter((ch) => ch && ch.key === this.param.dependsOn),
          tap(() => {
            this.values = [];
            this.labels = [];
            this.request.params[this.param.key] = null;
          }),
          filter((ch) => !!ch.value),
          switchMap((ch) => this.products.loadProductsOf(ch.value)),
        )
        .subscribe((ps) => {
          ps.forEach((p) => {
            if (this.values.indexOf(p.key.name) === -1) {
              this.values.push(p.key.name);
              this.labels.push(p.name);
            }
          });
        });
    }
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onModelChange(event: string) {
    this.changed$.next({ key: this.param.key, value: event });
  }
}
