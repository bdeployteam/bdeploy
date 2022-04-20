import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { CustomDataGrouping } from 'src/app/models/gen.dtos';

export function calculateGrouping<T>(
  definitions: BdDataGroupingDefinition<T>[],
  preset: CustomDataGrouping[],
  defaultGrouping: BdDataGrouping<T>[] = []
): BdDataGrouping<T>[] {
  const result: BdDataGrouping<T>[] = [];
  if (preset?.length) {
    for (const item of preset) {
      const def = definitions.find((d) => d.name === item.name);
      if (!def) {
        console.warn(
          'Grouping definition not (any longer?) available: ' + item.name
        );
        continue;
      }
      result.push({ definition: def, selected: item.selected });
    }
  }
  return result.length ? result : defaultGrouping;
}
