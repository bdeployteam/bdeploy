import { Pipe, PipeTransform } from '@angular/core';
import { isObject } from 'lodash-es';
import { ApplicationConfiguration } from 'src/app/models/gen.dtos';

@Pipe({
    name: 'nodeFilter',
    pure: false
})
export class NodeFilterPipe implements PipeTransform {
  transform(appConfigs: ApplicationConfiguration[], args: string): any {
    if (appConfigs) {
      if (args.length === 0) {
        return appConfigs;
      } else {
        return appConfigs.filter((appConfig) => searchThroughObject(appConfig, args));
      }
    }
  }
}

@Pipe({
    name: 'customNodeFilter',
    pure: false
})
export class CustomNodeFilterPipe implements PipeTransform {
  transform(item: unknown, args: any): boolean {
    if (item) {
      if (args.length === 0) {
        return true;
      } else {
        return searchThroughObject(item as object, args);
      }
    }
    return null;
  }
}

export function searchThroughObject(obj: object, args: string): boolean {
  return Object.values(obj).some((val: any) => {
    if (isObject(val)) {
      return searchThroughObject(val, args);
    }

    if (Array.isArray(val)) {
      val.forEach((item) => searchThroughObject(item, args));
    }

    if (val !== null) {
      return val.toString().toLowerCase().includes(args.toLowerCase());
    }
  });
}
