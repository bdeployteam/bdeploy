import { VariableConfiguration, VariableDescriptor } from 'src/app/models/gen.dtos';

export const UNGROUPED = 'Ungrouped Variables';
export const CUSTOM = 'Custom Variables';

export interface VariableGroup {
  name: string;
  pairs: VariablePair[];
  isCustom: boolean;
}

export interface CustomVariableGroup extends VariableGroup {
  isSelectMode: boolean;
}

export interface VariablePair {
  descriptor: VariableDescriptor;
  value: VariableConfiguration;

  editorEnabled: boolean;
}

export function groupVariables(descriptors: VariableDescriptor[], values: VariableConfiguration[]): VariableGroup[] {
  descriptors = descriptors || [];
  values = values || [];
  // group all variable descriptors and configurations together for simple iteration in the template.
  const groups: VariableGroup[] = [];
  for (const d of descriptors) {
    const grpName = d.groupName?.length ? d.groupName : UNGROUPED;
    let grp = groups.find((g) => g.name === grpName);
    if (!grp) {
      grp = {
        name: grpName,
        pairs: [],
        isCustom: false,
      };
      groups.push(grp);
    }

    const pair: VariablePair = {
      descriptor: d,
      value: values.find((v) => v.id === d.id),
      editorEnabled: true, // used to lock once custom editor is loaded.
    };

    if (!pair.value) {
      console.error(`variable descriptor ${d.name} does not have a matching value`);
    } else {
      grp.pairs.push(pair);
    }
  }

  // sort groups by name, ungrouped variables come last.
  groups.sort((a, b) => {
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
  const custom: CustomVariableGroup = {
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
  groups.push(custom);

  groups.forEach((group) => sortPairs(group.pairs));

  return groups;
}

function sortPairs(pairs: VariablePair[]): VariablePair[] {
  return pairs.sort((a, b) => {
    if (!!a?.descriptor?.name && !!b?.descriptor?.name) {
      return a.descriptor.name.localeCompare(b.descriptor.name);
    }

    const ida = a.descriptor?.id ? a.descriptor.id : a.value.id;
    const idb = b.descriptor?.id ? b.descriptor.id : b.value.id;

    return ida.localeCompare(idb);
  });
}
