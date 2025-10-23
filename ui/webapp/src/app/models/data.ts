import { Type } from '@angular/core';
import { SortDirection } from '@angular/material/sort';
import { CellComponent } from '../modules/core/components/bd-data-component-cell/bd-data-component-cell.component';
import { TooltipPosition } from '@angular/material/tooltip';

/**
 * A type hint for the colum which may (mostly in card mode) have influence on where and how information is rendered.
 */
export enum BdDataColumnTypeHint {
  /* The type, an uppercased text at the very top, should descripe the kind of object the card represents */
  TYPE,
  /* The title of the displayed object */
  TITLE,
  /* The description of the displayed object. Shown below the title */
  DESCRIPTION,
  /* A potential status, only works with a custom rendering component */
  STATUS,
  /* May appear multiple times: A 'detail' about the displayed object, rendered using the 'icon' and 'data' in a table */
  DETAILS,
  /* May appear multiple times: An action which can be performed on the displayed object, will trigger the columns 'action' */
  ACTIONS,
  /* Column's 'data' contains a URL to an image, which is to be shown as image */
  AVATAR,
  /* A footer text (single line, ellipsiszed) shown below the details and actions. */
  FOOTER,
}

/** Determines in which mode a column should be shown */
export enum BdDataColumnDisplay {
  TABLE = 'TABLE',
  CARD = 'CARD',
  BOTH = 'BOTH',
  NONE = 'NONE',
}

/**
 * Defines a column of the data table.
 *
 * @param <T> Type of the input that the data is extracted from
 * @param <R> Type of the data that will be displayed
 */
export interface BdDataColumn<T, R> {
  /** internal ID of the column, used to keep track of columns */
  id: string;

  /** The name of the column, usually only displayed in table mode or in filter selection */
  name: string;

  /** Receives a record, extracts the data to display for this column. Provides data for searching even if custom rendering component is used. */
  data: (record: T) => R;

  /** Additional tooltip callback which will be queried if given instead of displaying the data as tooltip */
  tooltip?: (record: T) => string;

  /** The position of the tooltip; defaults to 'left' */
  tooltipPosition?: TooltipPosition

  /** Provides a alternative rendering component for the cell, which has a reference to the Record and the Column. */
  component?: Type<CellComponent<T, R>>;

  /** The description of the column, usually displayed as a tooltip somewhere */
  description?: string;

  /** The columns width in CSS style (e.g. '36px', '8%', etc.) */
  width?: string;

  /** Show column in table mode when this media query is resolved */
  showWhen?: string;

  /** If set, render the contents of the column as button, which will trigger the given action when clicked. The data passed is the record object. */
  action?: (record: T) => void;

  /** If set, determines whether the action is disabled. */
  actionDisabled?: (record: T) => boolean;

  /** A callback to determine an icon for the cell - action or detail item. The data passed is the record object. */
  icon?: (record: T) => string;

  /** A callback to determine additional CSS classes to apply to the item, which may be either plain text, a button, etc. The data passed is the record object. */
  classes?: (record: T) => string[];

  /** A hint which determines where to place column contents on a card. */
  hint?: BdDataColumnTypeHint;

  /** In which mode the column should be displayed, both if unset */
  display?: BdDataColumnDisplay;

  /** Override tooltip delay in milliseconds. */
  tooltipDelay?: number;

  /** Set to true if you want to sort cards by this column */
  sortCard?: boolean;

  /** Whether the data of this column can be used to uniquely identify rows. */
  isId?: boolean;
}

/** The group used if a record does not match any group when grouping. */
export const UNMATCHED_GROUP = 'No Group';

export interface BdDataGroupingDefinition<T> {
  /** The name of the grouping, selectable by the user */
  name: string;

  /** The ID of the associated column, which will be hidden when the grouping is active. */
  associatedColumn?: string;

  /** determines the name of the group this grouping would put the record in. */
  group: (record: T) => string;

  /** provides sorting for the selected groups. the callback must be able to handle null, which is used for "No Group" */
  sort?: (a: string, b: string, recordsA?: T[], recordsB?: T[]) => number;
}

export interface BdDataGrouping<T> {
  /** The definition of the grouping */
  definition: BdDataGroupingDefinition<T>;

  /** The selected groups to show. If not set, show all groups */
  selected: string[];
}

/**
 * Find all possible group values from the given data set.
 *
 * Note that null is a possible value returned, which will be translated to "No Group" by the filter panel.
 */
export function bdExtractGroups<T>(grouping: BdDataGroupingDefinition<T>, records: T[]): string[] {
  if (!records) {
    return [];
  }
  const allGroups = records.map((r) => grouping.group(r)).map((r) => r || UNMATCHED_GROUP);
  return allGroups.filter((v, i, s) => s.indexOf(v) === i); // unique.
}

/**
 * Default null-capable group sorting. Nulls come last.
 */
export function bdSortGroups(a: string, b: string) {
  if (a === b) {
    return 0;
  }
  if (a === UNMATCHED_GROUP) {
    return 1;
  }
  if (b === UNMATCHED_GROUP) {
    return -1;
  }
  return a.localeCompare(b);
}

/** The default sorting algorithm which should be fine for almost all cases */
export function bdDataDefaultSort<T>(data: T[], column: BdDataColumn<T, unknown>, direction: SortDirection) {
  return data.sort((a, b) => {
    const dir = direction === 'asc' ? 1 : -1;

    const da = column.data(a);
    const db = column.data(b);

    if (da === db) {
      return 0;
    }
    if (!da) {
      return -1 * dir;
    }
    if (!db) {
      return 1 * dir;
    }

    if (da < db) {
      return -1 * dir;
    }
    if (da > db) {
      return 1 * dir;
    }

    throw new Error('Unreachable');
  });
}

/** The default search which uses case insensitive search in all fields of the record. */
export function bdDataDefaultSearch<T>(search: string, records: T[], columns: BdDataColumn<T, unknown>[]): T[] {
  if (!search) {
    return records;
  }

  const lowerSearch = search.toLowerCase();
  return records.filter((r) => {
    const value = createSearchString(r, columns);
    return value.includes(lowerSearch);
  });
}

/** Takes any object and produces a "searchable" string by joining all field values into a space separated string */
function createSearchString<T>(val: T, columns: BdDataColumn<T, unknown>[]): string {
  let result = '';
  if (!val) {
    return result;
  }
  for (const column of columns) {
    result += String(column.data(val)).toLowerCase() + ' ';
  }
  return result;
}
