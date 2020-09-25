import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { NgTerminalModule } from 'ng-terminal';
import { CoreModule } from '../core/core.module';
import { CustomAttributeEditComponent } from './components/custom-attribute-edit/custom-attribute-edit.component';
import { CustomAttributeValueComponent } from './components/custom-attribute-value/custom-attribute-value.component';
import { FileUploadComponent } from './components/file-upload/file-upload.component';
import { FileViewerComponent } from './components/file-viewer/file-viewer.component';
import { MessageboxComponent } from './components/messagebox/messagebox.component';
import { RemoteProgressElementComponent } from './components/remote-progress-element/remote-progress-element.component';
import { RemoteProgressComponent } from './components/remote-progress/remote-progress.component';
import { PortValidatorDirective } from './validators/port.validator';

@NgModule({
  declarations: [
    RemoteProgressComponent,
    RemoteProgressElementComponent,
    FileViewerComponent,
    MessageboxComponent,
    FileUploadComponent,
    PortValidatorDirective,
    CustomAttributeEditComponent,
    CustomAttributeValueComponent,
  ],
  entryComponents: [
    MessageboxComponent,
    FileUploadComponent,
    CustomAttributeEditComponent,
    CustomAttributeValueComponent,
  ],
  imports: [
    CommonModule,
    CoreModule,
    NgTerminalModule,
  ],
  exports: [
    RemoteProgressComponent,
    FileViewerComponent,
    MessageboxComponent,
    FileUploadComponent,
    PortValidatorDirective,
    CustomAttributeEditComponent,
    CustomAttributeValueComponent,
  ]
})
export class SharedModule { }
