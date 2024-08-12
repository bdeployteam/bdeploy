import { VariableConfiguration, VariableDescriptor } from 'src/app/models/gen.dtos';

export interface VariableGroup {
  name: string;
  pairs: VariablePair[];

  isCustom: boolean;
  isSelectMode: boolean;
}

export interface VariablePair {
  descriptor: VariableDescriptor;
  value: VariableConfiguration;

  editorEnabled: boolean;
}
