import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { NgTerminalModule } from 'ng-terminal';
import { MonacoEditorModule } from 'ngx-monaco-editor';
import { CoreModule } from '../../core/core.module';
import { CustomAttributeEditComponent } from './components/custom-attribute-edit/custom-attribute-edit.component';
import { CustomAttributeGroupingSelectorComponent } from './components/custom-attribute-grouping-selector/custom-attribute-grouping-selector.component';
import { CustomAttributeValueComponent } from './components/custom-attribute-value/custom-attribute-value.component';
import { DiffEditorComponent } from './components/diff-editor/diff-editor.component';
import { FileEditorComponent } from './components/file-editor/file-editor.component';
import { FileUploadComponent } from './components/file-upload/file-upload.component';
import { FileViewerComponent } from './components/file-viewer/file-viewer.component';
import { ProductCardComponent } from './components/product-card/product-card.component';
import { ProductListComponent } from './components/product-list/product-list.component';
import { RemoteProgressElementComponent } from './components/remote-progress-element/remote-progress-element.component';
import { RemoteProgressComponent } from './components/remote-progress/remote-progress.component';
import { TextboxComponent } from './components/textbox/textbox.component';
import { PortValidatorDirective } from './validators/port.validator';

@NgModule({
  declarations: [
    RemoteProgressComponent,
    RemoteProgressElementComponent,
    FileViewerComponent,
    FileUploadComponent,
    PortValidatorDirective,
    CustomAttributeEditComponent,
    CustomAttributeValueComponent,
    CustomAttributeGroupingSelectorComponent,
    ProductCardComponent,
    ProductListComponent,
    FileEditorComponent,
    DiffEditorComponent,
    TextboxComponent,
  ],
  entryComponents: [FileUploadComponent, CustomAttributeEditComponent, CustomAttributeValueComponent],
  imports: [CommonModule, CoreModule, NgTerminalModule, MonacoEditorModule.forRoot()],
  exports: [
    RemoteProgressComponent,
    FileViewerComponent,
    FileUploadComponent,
    PortValidatorDirective,
    CustomAttributeEditComponent,
    CustomAttributeValueComponent,
    CustomAttributeGroupingSelectorComponent,
    ProductCardComponent,
    ProductListComponent,
    FileEditorComponent,
    DiffEditorComponent,
    TextboxComponent,
  ],
})
export class SharedModule {}
