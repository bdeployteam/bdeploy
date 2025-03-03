import { SelectionModel } from '@angular/cdk/collections';
import { FlatTreeControl } from '@angular/cdk/tree';
import { AfterViewInit, Component, EventEmitter, inject, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import {
  MatTree,
  MatTreeFlatDataSource,
  MatTreeFlattener,
  MatTreeNode,
  MatTreeNodeDef,
  MatTreeNodePadding,
  MatTreeNodeToggle
} from '@angular/material/tree';
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

export class DirTreeNode {
  name: string;
  level: number;
  expandable: boolean;
}

@Component({
    selector: 'app-config-process-header',
    templateUrl: './config-process-header.component.html',
    styleUrls: ['./config-process-header.component.css'],
  imports: [MatCard, MatTree, MatTreeNodeDef, MatTreeNode, MatTreeNodeToggle, MatTreeNodePadding, MatIconButton, MatCheckbox, MatIcon, BdButtonComponent, FormsModule, ConfigDescElementComponent, BdFormInputComponent, TrimmedValidator, EditProcessNameValidatorDirective, BdFormSelectComponent, BdFormToggleComponent, MatTooltip, EditItemInListValidatorDirective, ClickStopPropagationDirective, BdPopupDirective, AsyncPipe]
})
export class ConfigProcessHeaderComponent implements OnInit, OnDestroy, AfterViewInit {
  private readonly instances = inject(InstancesService);
  protected readonly edit = inject(ProcessEditService);

  @ViewChild('form') public form: NgForm;
  @ViewChild('dirSelector', { static: false }) private readonly dirSelector: BdPopupDirective;
  @Output() checkIsInvalid = new EventEmitter<boolean>();

  protected app: ApplicationDto;
  protected startTypes: ApplicationStartType[];
  protected startTypeLabels: string[];

  protected dirFlattener: MatTreeFlattener<ConfigDirDto, DirTreeNode>;
  protected dirTreeControl: FlatTreeControl<DirTreeNode>;
  protected dirDataSource: MatTreeFlatDataSource<ConfigDirDto, DirTreeNode>;
  protected dirSelection = new SelectionModel<DirTreeNode>(true /* multiple */);
  protected dirFlatAllowedValues: string[];

  private subscription: Subscription;

  private readonly dirTransformer = (node: ConfigDirDto, level: number): DirTreeNode => {
    return {
      name: node.name,
      level: level,
      expandable: !!node.children?.length,
    };
  };

  protected hasChild = (_: number, _nodeData: DirTreeNode) => _nodeData.expandable;

  ngOnInit(): void {
    this.dirFlattener = new MatTreeFlattener<ConfigDirDto, DirTreeNode>(
      this.dirTransformer,
      (n) => n.level,
      (n) => n.expandable,
      (n) => n.children,
    );
    this.dirTreeControl = new FlatTreeControl(
      (n) => n.level,
      (n) => n.expandable,
    );
    this.dirDataSource = new MatTreeFlatDataSource(this.dirTreeControl, this.dirFlattener);

    this.subscription = this.edit.application$.subscribe((application) => {
      this.app = application;
      this.startTypes = this.getStartTypes(this.app);
      this.startTypeLabels = this.getStartTypeLabels(this.app);
    });

    this.subscription.add(
      this.instances.current$.pipe(skipWhile((i) => !i)).subscribe((i) => {
        this.dirDataSource.data = !i.configRoot ? [] : [i.configRoot];
        this.dirTreeControl.dataNodes.filter((n) => n.level === 0).forEach((n) => this.dirTreeControl.expand(n));
        this.dirFlatAllowedValues = ['/', ...this.buildFlatAllowedValues(i.configRoot, [''])];
      }),
    );
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.statusChanges.pipe(debounceTime(100)).subscribe((status) => {
        this.checkIsInvalid.emit(status !== 'VALID');
      }),
    );
    this.checkIsInvalid.emit(this.form.status !== 'VALID');
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private buildFlatAllowedValues(dir: ConfigDirDto, path: string[]): string[] {
    if (!dir) {
      return [];
    }
    if (dir.children?.length) {
      return dir.children.flatMap((c) => this.buildFlatAllowedValues(c, [...path, c.name]));
    } else {
      return path.map((_, i) => path.slice(0, i + 1).join('/')).filter((v) => !!v);
    }
  }

  protected onDirSelectorOpened(dirs: string) {
    this.dirSelection.clear();

    if (!dirs?.trim()?.length) {
      return; // empty - nothing to select.
    }

    const toSelect: string[][] = dirs === '/' ? [['/']]:
      (dirs || '').split(',').map((d) => d.trim().split('/'));
    toSelect.forEach((n) => this.selectLeaf(n));
  }

  private selectLeaf(path: string[]) {
    const rootNodes = this.dirTreeControl.dataNodes.filter((n) => n.level === 0);
    rootNodes.forEach((n) => this.findLeafAndSelect(n, path));
  }

  private findLeafAndSelect(node: DirTreeNode, path: string[]) {
    if (path?.length < node.level || (node.level > 0 && node.name !== path[node.level])) {
      return; // nope
    }

    // we're on the right track.
    if (path.length === node.level + 1 && !node.expandable) {
      this.dirSelection.select(node);
      return;
    }

    // need to go one level deeper.
    if (node.expandable) {
      this.dirTreeControl
        .getDescendants(node)
        .filter((n) => n.level === node.level + 1)
        .forEach((n) => this.findLeafAndSelect(n, path));
    }
  }

  protected doApplyDirectories() {
    const rootNodes = this.dirTreeControl.dataNodes.filter((n) => n.level === 0);
    const selectedLeafs: string[] = [];
    for (const root of rootNodes) {
      // find selected leafs. empty root will join to '/'
      selectedLeafs.push(...this.getSelectedLeafPaths(root, ['']));
    }
    this.edit.process$.value.processControl.configDirs = selectedLeafs.join(',');
    this.dirSelector.closeOverlay();
  }

  private getSelectedLeafPaths(node: DirTreeNode, path: string[]): string[] {
    if (
      this.dirSelection.isSelected(node) ||
      this.dirTreeControl.getDescendants(node).some((n) => this.dirSelection.isSelected(n))
    ) {
      if (node.expandable) {
        return this.dirTreeControl
          .getDescendants(node)
          .filter((n) => n.level === node.level + 1)
          .flatMap((n) => this.getSelectedLeafPaths(n, [...path, n.name]));
      } else {
        if (path?.length === 1 && path[0] === '') {
          // root node.
          return ['/'];
        }
        // single leaf node, build path.
        return [path.join('/')];
      }
    }
    return [];
  }

  /** Whether all the descendants of the node are selected */
  protected descendantsAllSelected(node: DirTreeNode): boolean {
    const descendants = this.dirTreeControl.getDescendants(node);
    return descendants.every((child) => this.dirSelection.isSelected(child));
  }

  /** Whether part of the descendants are selected */
  protected descendantsPartiallySelected(node: DirTreeNode): boolean {
    const descendants = this.dirTreeControl.getDescendants(node);
    const result = descendants.some((child) => this.dirSelection.isSelected(child));
    return result && !this.descendantsAllSelected(node);
  }

  /** Toggle the directory item selection. Select/deselect all the descendants node */
  protected dirItemSelectionToggle(node: DirTreeNode): void {
    this.dirSelection.toggle(node);
    const descendants = this.dirTreeControl.getDescendants(node);
    if(this.dirSelection.isSelected(node)) {
      this.dirSelection.select(...descendants);
    } else {
      this.dirSelection.deselect(...descendants);
    }
  }

  private getStartTypes(app: ApplicationDto): ApplicationStartType[] {
    const supported = app?.descriptor?.processControl?.supportedStartTypes;
    if (!supported?.length || !!supported.find((s) => s === ApplicationStartType.INSTANCE)) {
      return [ApplicationStartType.INSTANCE, ApplicationStartType.MANUAL, ApplicationStartType.MANUAL_CONFIRM];
    } else if (supported.find((s) => s === ApplicationStartType.MANUAL)) {
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
