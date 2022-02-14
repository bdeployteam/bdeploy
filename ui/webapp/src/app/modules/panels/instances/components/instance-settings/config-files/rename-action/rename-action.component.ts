import {
  Component,
  forwardRef,
  Inject,
  Input,
  OnInit,
  TemplateRef,
  ViewChild,
} from '@angular/core';
import { FileStatusType } from 'src/app/models/gen.dtos';
import {
  ACTION_CANCEL,
  ACTION_CONFIRM,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdFormInputComponent } from 'src/app/modules/core/components/bd-form-input/bd-form-input.component';
import {
  ConfigFile,
  ConfigFilesService,
} from '../../../../services/config-files.service';
import { ConfigFilesComponent } from '../config-files.component';

@Component({
  selector: 'app-rename-action',
  templateUrl: './rename-action.component.html',
})
export class RenameActionComponent implements OnInit {
  @Input() record: ConfigFile;

  /* template */ newName: string;
  /* template */ renameAllowed: boolean;

  @ViewChild('renameInput', { static: false })
  private renameInput: BdFormInputComponent;

  constructor(
    private cfgFiles: ConfigFilesService,
    @Inject(forwardRef(() => ConfigFilesComponent))
    private parent: ConfigFilesComponent
  ) {}

  ngOnInit(): void {
    this.renameAllowed = this.canRename();
  }

  /* template */ onRename(tpl: TemplateRef<any>): void {
    const oldName = this.cfgFiles.getPath(this.record);
    this.newName = oldName;

    this.parent.dialog
      .message({
        header: `Rename ${oldName}`,
        template: tpl,
        validation: () =>
          !this.renameInput ? false : !this.renameInput.isInvalid(),
        actions: [ACTION_CANCEL, ACTION_CONFIRM],
      })
      .subscribe((r) => {
        if (!r) {
          return;
        }
        this.cfgFiles.load(oldName).subscribe((content) => {
          if (!confirm) {
            return;
          }

          const isBin = !this.cfgFiles.isText(this.cfgFiles.get(oldName));
          this.cfgFiles.move(oldName, this.newName, content, isBin);
        });
      });
  }

  private canRename(): boolean {
    if (this.record.modification?.type === FileStatusType.DELETE) {
      return false;
    }

    return true;
  }
}
