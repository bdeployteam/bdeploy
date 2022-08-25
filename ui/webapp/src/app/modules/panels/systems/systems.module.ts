import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { AddSystemComponent } from './components/add-system/add-system.component';
import { SystemDetailsComponent } from './components/system-details/system-details.component';
import { SystemEditComponent } from './components/system-details/system-edit/system-edit.component';
import { SystemVariablesComponent } from './components/system-details/system-variables/system-variables.component';
import { SystemsRoutingModule } from './systems-routing.module';
import { VariableServerValidatorDirective } from './validators/variable-server-validator.directive';

@NgModule({
  declarations: [
    AddSystemComponent,
    SystemDetailsComponent,
    SystemEditComponent,
    SystemVariablesComponent,
    VariableServerValidatorDirective,
  ],
  imports: [CommonModule, SystemsRoutingModule, CoreModule],
})
export class SystemsModule {}
