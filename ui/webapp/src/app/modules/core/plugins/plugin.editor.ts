import { Api } from './plugin.api';

export interface EditorPlugin {
  // onRead = plugin reads value to be edited (initially).
  // onUpdate = called when plugin updates the valued.
  bind(
    onRead: () => string,
    onUpdate: (value: string) => void,
    onValidStateChange: (valid: boolean) => void,
  ): HTMLElement;
}

export type EditorPluginConstructor = new (api: Api) => EditorPlugin;

export interface EditorPluginModule {
  default: EditorPluginConstructor
}
