import { VariableConfiguration, VariableDescriptor } from 'src/app/models/gen.dtos';

export const UNGROUPED = 'Ungrouped Variables';
export const CUSTOM = 'Custom Variables';

export interface VariableGroup {
  name: string;
  pairs: VariablePair[];

  isCustom: boolean;
}

export interface VariablePair {
  descriptor: VariableDescriptor;
  value: VariableConfiguration;

  editorEnabled: boolean;
}

export function groupVariables(descriptors: VariableDescriptor[], values: VariableConfiguration[]): VariableGroup[] {
  // group all variable descriptors and configurations together for simple iteration in the template.
  const r: VariableGroup[] = [];
  for (const d of descriptors) {
    const grpName = d.groupName?.length ? d.groupName : UNGROUPED;
    let grp = r.find((g) => g.name === grpName);
    if (!grp) {
      grp = {
        name: grpName,
        pairs: [],
        isCustom: false,
      };
      r.push(grp);
    }

    const pair: VariablePair = {
      descriptor: d,
      value: values.find((v) => v.id === d.id),
      editorEnabled: true, // used to lock once custom editor is loaded.
    };
    grp.pairs.push(pair);
  }

  // sort groups by name, ungrouped variables come last.
  r.sort((a, b) => {
    if (a?.name === b?.name) {
      return 0;
    }

    if (a?.name === UNGROUPED) {
      return 1;
    } else if (b?.name === UNGROUPED) {
      return -1;
    }

    return a?.name?.localeCompare(b?.name);
  });

  // find custom variables and add them
  const custom = {
    name: CUSTOM,
    pairs: [],
    isCustom: true,
    isSelectMode: false,
  };
  for (const v of values) {
    if (!descriptors?.find((d) => d.id === v.id)) {
      // no descriptor -> custom
      custom.pairs.push({
        descriptor: null,
        value: v,
        editorEnabled: true,
      });
    }
  }

  // *always* add custom variables, even if none are there yet to allow adding some later. custom variables come even after ungrouped ones.
  r.push(custom);

  return r;
}
