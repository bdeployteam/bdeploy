import { Api } from './plugin.api';

export interface EditorPlugin {
  new(api: Api);

  // onRead = plugin reads value to be edited (initially).
  // onUpdate = called when plugin updates the valued.
  bind(onRead: () => string, onUpdate: (value: string) => void, onValidStateChange: (valid: boolean) => void): HTMLElement;
}
