import { Pipe, PipeTransform } from '@angular/core';
import { isObject } from 'lodash-es';
import { ApplicationConfiguration } from 'src/app/models/gen.dtos';

@Pipe({
  name: 'nodeFilter',
  pure: false,
})
export class NodeFilterPipe implements PipeTransform {
  transform(items: ApplicationConfiguration[], args: string): any {
    if (items) {
      if (args.length === 0) {
        return items;
      } else {
        return items.filter((obj) => searchThroughObject(obj, args));
      }
    }
  }
}

@Pipe({
  name: 'customNodeFilter',
  pure: false,
})
export class CustomNodeFilterPipe implements PipeTransform {
  transform(item: any, args: any): any {
    if (item) {
      if (args.length === 0) {
        return item;
      } else {
        return searchThroughObject(item, args);
      }
    }
  }
}

export function searchThroughObject(obj, args) {
  return Object.values(obj).some((val) => {
    if (isObject(val)) {
      return searchThroughObject(val, args);
    }

    if (Array.isArray(val)) {
      val.forEach((item) => {
        return searchThroughObject(item, args);
      });
    }

    if (val !== null) {
      return val.toString().toLowerCase().includes(args.toLowerCase());
    }
  });
}
