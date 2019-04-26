import { BehaviorSubject } from 'rxjs';

/**
 * A list that allows to register a custom callback to filter the data.
 */
export class DataList<T> {

  public searchString: string;
  public searchCallback: (item: T, text: string) => boolean;
  public searchChange: BehaviorSubject<string> = new BehaviorSubject(null);

  public data: T[] = [];
  public filtered: T[] = [];

  public addAll(data: T[]): void {
    this.data = data;
    this.filtered = data;
  }

  public clear(): void {
    this.data = [];
    this.filtered = [];
  }

  public remove(predicate: (value: T, index: number, obj: T[]) => boolean): void {
    const index = this.data.findIndex(predicate);
    if (index === -1) {
      return;
    }
    this.data.splice(index, 1);

    const filteredIndex = this.filtered.findIndex(predicate);
    if (filteredIndex === -1) {
      return;
    }
    this.filtered.splice(filteredIndex, 1);
  }

  public applyFilter(): void {
    this.filtered = this.data.filter(item => this.searchCallback(item, this.searchString.toLowerCase()));
    this.searchChange.next(this.searchString);
  }

  public isEmpty(): boolean {
    return this.data.length === 0;
  }

}
