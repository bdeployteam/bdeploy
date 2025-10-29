import { SelectionModel } from '@angular/cdk/collections';
import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  inject,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
} from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { MatTree, MatTreeNode, MatTreeNodeDef, MatTreeNodePadding, MatTreeNodeToggle } from '@angular/material/tree';
import { debounceTime, skipWhile, Subscription } from 'rxjs';
import { ApplicationDto, ApplicationStartType, ConfigDirDto } from 'src/app/models/gen.dtos';
import { BdPopupDirective } from 'src/app/modules/core/components/bd-popup/bd-popup.directive';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProcessEditService } from '../../../../services/process-edit.service';
import { MatCard } from '@angular/material/card';
import { MatIconButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatIcon } from '@angular/material/icon';
import { BdButtonComponent } from '../../../../../../core/components/bd-button/bd-button.component';
import { ConfigDescElementComponent } from '../../../config-desc-element/config-desc-element.component';
import { BdFormInputComponent } from '../../../../../../core/components/bd-form-input/bd-form-input.component';
import { TrimmedValidator } from '../../../../../../core/validators/trimmed.directive';
import { EditProcessNameValidatorDirective } from '../../../../validators/edit-process-name-validator.directive';
import { BdFormSelectComponent } from '../../../../../../core/components/bd-form-select/bd-form-select.component';
import { BdFormToggleComponent } from '../../../../../../core/components/bd-form-toggle/bd-form-toggle.component';
import { MatTooltip } from '@angular/material/tooltip';
import { EditItemInListValidatorDirective } from '../../../../../../core/validators/edit-item-in-list.directive';
import { ClickStopPropagationDirective } from '../../../../../../core/directives/click-stop-propagation.directive';

import { AsyncPipe } from '@angular/common';

/**
 * We will generate a path using the name, so that we can determine if
 * the node is selected, and only add leafs to the selection.
 */
interface DirTreeNode extends ConfigDirDto {
  path: string;
  children: DirTreeNode[];
}

@Component({
  selector: 'app-config-process-header',
  templateUrl: './config-process-header.component.html',
  styleUrls: ['./config-process-header.component.css'],
  imports: [
    MatCard,
    MatTree,
    MatTreeNodeDef,
    MatTreeNode,
    MatTreeNodeToggle,
    MatTreeNodePadding,
    MatIconButton,
    MatCheckbox,
    MatIcon,
    BdButtonComponent,
    FormsModule,
    ConfigDescElementComponent,
    BdFormInputComponent,
    TrimmedValidator,
    EditProcessNameValidatorDirective,
    BdFormSelectComponent,
    BdFormToggleComponent,
    MatTooltip,
    EditItemInListValidatorDirective,
    ClickStopPropagationDirective,
    BdPopupDirective,
    AsyncPipe,
  ],
})
export class ConfigProcessHeaderComponent implements OnInit, OnDestroy, AfterViewInit {
  private readonly instances = inject(InstancesService);
  protected readonly edit = inject(ProcessEditService);
  private readonly changeDetectorRef = inject(ChangeDetectorRef);

  @ViewChild('form') public form: NgForm;
  @ViewChild('dirSelector', { static: false }) private readonly dirSelector: BdPopupDirective;
  @Output() checkIsInvalid = new EventEmitter<boolean>();

  protected app: ApplicationDto;
  protected startTypes: ApplicationStartType[];
  protected startTypeLabels: string[];

  @ViewChild(MatTree) matTree?: MatTree<DirTreeNode>;
  protected dirDataSource: DirTreeNode[] = [];
  protected dirSelection = new SelectionModel<DirTreeNode>(true /* multiple */);
  protected dirFlatAllowedValues: string[] = [];

  private subscription: Subscription;

  protected childrenAccessor = (node: ConfigDirDto) => node.children ?? [];

  protected hasChild = (_: number, node: ConfigDirDto) => !!node.children?.length;

  protected isExpandable = (node: ConfigDirDto) => this.hasChild(null, node);

  ngOnInit(): void {
    this.subscription = this.edit.application$.subscribe((application) => {
      this.app = application;
      this.startTypes = this.getStartTypes(this.app);
      this.startTypeLabels = this.getStartTypeLabels(this.app);
    });

    this.subscription.add(
      this.instances.current$.pipe(skipWhile((i) => !i)).subscribe((i) => {
        if (i.configRoot) {
          this.dirDataSource = [i.configRoot as DirTreeNode];
          this.generateTreeNodesAndAllowedValues('', i.configRoot as DirTreeNode);
        } else {
          this.dirDataSource = [];
        }
      })
    );
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.statusChanges.pipe(debounceTime(100)).subscribe((status) => {
        this.checkIsInvalid.emit(status !== 'VALID');
      })
    );

    this.checkIsInvalid.emit(this.form.status !== 'VALID');
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private generateTreeNodesAndAllowedValues(parentPath: string, node: DirTreeNode) {
    // this is a super special way of making sure we don't append "/" to the root "/"
    const suffixedParentPath = parentPath.length > 1 ? parentPath + '/' : parentPath;
    node.path = suffixedParentPath + node.name;
    if (this.isExpandable(node)) {
      node.children.forEach((child) => this.generateTreeNodesAndAllowedValues(node.path, child));
    } else {
      // only leafs are allowed as values
      this.dirFlatAllowedValues.push(node.path);
    }
  }

  protected onDirSelectorOpened(dirs: string) {
    // redo selection
    this.dirSelection.clear();
    if (dirs?.trim()?.length) {
      const pathsAlreadySelected: string[] = dirs === '/' ? ['/'] : dirs.split(',');
      pathsAlreadySelected.forEach((path) => this.dirDataSource.forEach((node) => this.findLeafAndSelect(node, path)));
    }

    this.changeDetectorRef.detectChanges();
    this.matTree?.expand(this.dirDataSource[0]);
  }

  private findLeafAndSelect(node: DirTreeNode, pathToSelect: string) {
    if (this.isExpandable(node)) {
      // if expandable check children
      node.children.forEach((child) => this.findLeafAndSelect(child, pathToSelect));
    } else if (node.path === pathToSelect) {
      // if found leaf and matched the path
      this.dirSelection.select(node);
    }
  }

  protected doApplyDirectories() {
    const selectedLeafs: string[] = this.dirSelection.selected
      .filter((node) => !this.isExpandable(node))
      .map((node) => node.path);
    this.edit.process$.value.processControl.configDirs = selectedLeafs.join(',');
    this.dirSelector.closeOverlay();
  }

  protected hasAnyChildSelected(node: DirTreeNode): boolean {
    // if leaf, check if selected otherwise check children
    return this.isExpandable(node)
      ? node.children.some((child) => this.hasAnyChildSelected(child))
      : this.dirSelection.isSelected(node);
  }

  /** Whether all the descendants of the node are selected */
  protected descendantsAllSelected(node: DirTreeNode): boolean {
    if (this.isExpandable(node)) {
      return node.children.every((child) => this.descendantsAllSelected(child));
    } else {
      return this.dirSelection.isSelected(node);
    }
  }

  /** Whether part of the descendants are selected */
  protected descendantsPartiallySelected(node: DirTreeNode): boolean {
    const result = node.children.some((child) => this.hasAnyChildSelected(child));
    return result && !this.descendantsAllSelected(node);
  }

  /** Toggle the directory item selection. Select/deselect all the descendants nodes */
  protected dirItemSelectionToggle(node: DirTreeNode): void {
    this.matTree.expand(node);
    this.setSelection(!this.descendantsAllSelected(node), node);
    this.changeDetectorRef.detectChanges();
  }

  private setSelection(shouldSelect: boolean, node: DirTreeNode) {
    if (this.isExpandable(node)) {
      node.children.forEach((child) => {
        this.setSelection(shouldSelect, child);
      });
    } else if (shouldSelect) {
      this.dirSelection.select(node);
    } else {
      this.dirSelection.deselect(node);
    }
  }

  private getStartTypes(app: ApplicationDto): ApplicationStartType[] {
    const supported = app?.descriptor?.processControl?.supportedStartTypes;
    if (!supported?.length || !!supported.includes(ApplicationStartType.INSTANCE)) {
      return [ApplicationStartType.INSTANCE, ApplicationStartType.MANUAL, ApplicationStartType.MANUAL_CONFIRM];
    } else if (supported.includes(ApplicationStartType.MANUAL)) {
      return [ApplicationStartType.MANUAL, ApplicationStartType.MANUAL_CONFIRM];
    } else {
      return supported;
    }
  }

  private getStartTypeLabels(app: ApplicationDto): string[] {
    return this.getStartTypes(app).map((t) => {
      switch (t) {
        case ApplicationStartType.INSTANCE:
          return 'Instance (Automatic)';
        case ApplicationStartType.MANUAL:
          return 'Manual';
        case ApplicationStartType.MANUAL_CONFIRM:
          return 'Manual (with confirmation)';
      }
    });
  }
}
