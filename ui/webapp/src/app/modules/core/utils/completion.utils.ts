import {
  ApplicationConfiguration,
  ApplicationDto,
  InstanceConfigurationDto,
  SystemConfiguration,
} from 'src/app/models/gen.dtos';
import { ContentCompletion } from '../components/bd-content-assist-menu/bd-content-assist-menu.component';
import {
  LinkVariable,
  gatherPathExpansions,
  gatherProcessExpansions,
  gatherSpecialExpansions,
  gatherVariableExpansions,
} from './linked-values.utils';

const RECURSIVE_PREFIXES = ['DELAYED', 'JSON', 'XML', 'YAML'];

export function getRecursivePrefix(
  word: string,
  acc: string,
  recursivePrefixes: string[] = RECURSIVE_PREFIXES,
): string {
  for (const prefix of recursivePrefixes) {
    if (word.startsWith(acc + prefix + ':')) {
      return getRecursivePrefix(word, acc + prefix + ':');
    }
  }
  return acc;
}

export function buildCompletionPrefixes(): ContentCompletion[] {
  return [
    {
      value: '{{A:',
      icon: 'apps',
      description: 'Application Properties',
    },
    {
      value: '{{DELAYED:',
      icon: 'schedule',
      description: 'Delayed expansion of another expression',
    },
    {
      value: '{{ENV:',
      icon: 'dns',
      description: 'Target Node Environment Variable',
    },
    {
      value: '{{H:',
      icon: 'dns',
      description: 'Target Node Host Properties',
    },
    {
      value: '{{I:',
      icon: 'settings_system_daydream',
      description: 'Instance Properties',
    },
    {
      value: '{{IF:',
      icon: 'help',
      description: 'Conditional Expression',
    },
    {
      value: '{{LINUX:',
      icon: 'devices_other',
      description: 'Linux specific value',
    },
    {
      value: '{{M:',
      icon: 'folder_special',
      description: 'Manifest Reference',
    },
    { value: '{{P:', icon: 'folder', description: 'Deployment Target Paths' },
    { value: '{{V:', icon: 'build', description: 'Process Parameters' },
    {
      value: '{{WINDOWS:',
      icon: 'devices_other',
      description: 'Windows specific value',
    },
    {
      value: '{{X:',
      icon: 'data_object',
      description: 'Instance & System Variables',
    },
    {
      value: '{{FILEURI:',
      icon: 'folder',
      description: 'Transforms the given path into a file URI',
    },
    {
      value: '{{XML:',
      icon: 'special_character',
      description: 'Escape special XML characters',
    },
    {
      value: '{{JSON:',
      icon: 'special_character',
      description: 'Escape special JSON characters',
    },
    {
      value: '{{YAML:',
      icon: 'special_character',
      description: 'Escape special YAML characters',
    },
  ];
}

export function buildCompletions(
  prefixes: ContentCompletion[],
  instance: InstanceConfigurationDto,
  system: SystemConfiguration,
  process: ApplicationConfiguration,
  apps: ApplicationDto[],
): ContentCompletion[] {
  return [
    ...gatherVariableExpansions(instance, system).map((l) => ({
      value: l.link,
      icon: 'data_object',
      description: l.description,
    })),
    ...gatherProcessExpansions(instance, process, apps).map((l) => ({
      value: l.link,
      icon: 'build',
      description: l.description,
    })),
    ...gatherPathExpansions(instance).map((l) => ({
      value: l.link,
      icon: 'folder',
      description: l.description,
    })),
    ...gatherSpecialExpansions(instance, process, system).map((l) => ({
      value: l.link,
      icon: getPrefixIcon(l, prefixes),
      description: l.description,
    })),
  ].sort((a, b) => a.value.localeCompare(b.value));
}

export function getPrefixIcon(l: LinkVariable, prefixes: ContentCompletion[]): string {
  // find prefix and use its icon;
  const prefix = prefixes.find((p) => l.link.startsWith(p.value));
  return prefix?.icon;
}
