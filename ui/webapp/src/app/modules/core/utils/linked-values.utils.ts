import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import {
  ApplicationConfiguration,
  ApplicationDto,
  InstanceConfigurationDto,
  LinkedValueConfiguration,
  OperatingSystem,
  ParameterConfiguration,
  ParameterType,
  SystemConfiguration,
} from 'src/app/models/gen.dtos';
import { SpecialCharacterType, escapeSpecialCharacters } from './escape-special-characters.utils';
import { getAppOs } from './manifest.utils';

export function createLinkedValue(val: string): LinkedValueConfiguration {
  return !!val && val.indexOf('{{') != -1 ? { linkExpression: val, value: null } : { linkExpression: null, value: val };
}

export function getPreRenderable(val: LinkedValueConfiguration): string {
  return val?.linkExpression ? val?.linkExpression : val?.value;
}

/** returns the pre-renderable (non-expanded) value, but masks out passwords in case a direct value is used. */
export function getMaskedPreRenderable(val: LinkedValueConfiguration, type: ParameterType): string {
  return val?.linkExpression ? val?.linkExpression : maskIfPassword(val?.value, type);
}

export class LinkVariable {
  name: string;
  description: string;
  preview: string;
  link: string;
  group: string;
  matches?: (string) => boolean;
  expand?: (string) => string;
}

export function getRenderPreview(
  val: LinkedValueConfiguration,
  process: ApplicationConfiguration,
  instance: InstanceConfigurationDto,
  system: SystemConfiguration,
): string {
  if (val.linkExpression) {
    // need to expand. gather all supported expansions for the preview.
    const expansions: LinkVariable[] = [];

    expansions.push(...gatherVariableExpansions(instance, system));
    expansions.push(...gatherProcessExpansions(instance, process));
    expansions.push(...gatherPathExpansions(instance));
    expansions.push(...gatherSpecialExpansions(instance, process, system));

    // now expand the expression
    return expand(val.linkExpression, expansions);
  }
  return val.value;
}

function expand(val: string, vars: LinkVariable[]): string {
  let exp = val;
  let count = 0;

  while (exp && exp.match(/{{[^}]+}}/)) {
    const match = exp.match(/{{[^}]+}}/);
    const varName = match[0];
    const varVal = vars.find((v) => {
      if (v.matches) {
        return v.matches(varName);
      }
      return v.link == varName;
    });

    let x = exp;
    if (varVal) {
      if (varVal.expand) {
        x = exp.replace(match[0], varVal.expand(varName));
      } else {
        x = exp.replace(match[0], varVal.preview);
      }
    }

    // found *some* expansion but could not expand any further, either because no value
    // was found, or because of a circular reference.
    if (x === exp || count++ > 20) {
      return exp;
    }
    exp = x;
  }
  return exp;
}

export function gatherVariableExpansions(
  instance: InstanceConfigurationDto,
  system: SystemConfiguration,
): LinkVariable[] {
  const result: LinkVariable[] = [];

  if (instance?.config?.instanceVariables?.length) {
    instance?.config.instanceVariables
      .map((v) => {
        return {
          name: v.id,
          description: `Instance Variable - ${instance?.config?.name}`,
          preview: getMaskedPreRenderable(v.value, v.type), // explicitly the non-expanded value.
          link: `{{X:${v.id}}}`,
          group: null,
        };
      })
      .forEach((v) => {
        result.push(v);
      });
  }

  if (system?.systemVariables?.length) {
    system.systemVariables
      .map((v) => {
        return {
          name: v.id,
          description: `System Variable - ${system?.name}`,
          preview: getMaskedPreRenderable(v.value, v.type), // explicitly the non-expanded value.
          link: `{{X:${v.id}}}`,
          group: null,
        };
      })
      .forEach((v) => {
        if (result.findIndex((x) => x.name === v.name) < 0) {
          result.push(v);
        }
      });
  }

  return result;
}

function maskIfPassword(value: string, type: ParameterType) {
  if (type === ParameterType.PASSWORD && value) {
    return '*'.repeat(value.length);
  }

  return value ? value : '';
}

export function gatherProcessExpansions(
  instance: InstanceConfigurationDto,
  process: ApplicationConfiguration,
  apps: ApplicationDto[] = [],
): LinkVariable[] {
  if (!instance?.nodeDtos?.length) {
    return [];
  }

  const result: LinkVariable[] = [];

  // need to fetch them *directly* as well as through nodes below in case the application has not yet
  // been added to a node (i.e. resolving *while* creating an application).
  if (process?.start?.parameters?.length) {
    for (const param of process.start.parameters) {
      // process parameter, make sure we always use the unqualified version ("This Application")
      processParameter(true, process, param, apps, result);
    }
  }

  for (const node of instance.nodeDtos) {
    for (const app of node.nodeConfiguration.applications) {
      if (node.nodeName === CLIENT_NODE_NAME && app.name === process?.name && app.id !== process?.id) {
        // client app for different OS - this is actually not well supported, we cannot resolve parameters of this.
        continue;
      }
      for (const param of app.start.parameters) {
        if (app.id === process?.id) {
          // we already have the unqualified version in the expansions.
          continue;
        }

        // process parameter, always add full qualified name.
        processParameter(false, app, param, apps, result);
      }
    }
  }
  return result;
}

function processParameter(
  thisApp: boolean,
  app: ApplicationConfiguration,
  param: ParameterConfiguration,
  apps: ApplicationDto[],
  result: LinkVariable[],
) {
  let link = `{{V:${app.name}:${param.id}}}`;
  let group = app.name;
  if (thisApp) {
    link = `{{V:${param.id}}}`;
    group = `${app.name} (This Application)`;
  }

  // just for display - this is OK as value and expression, no need to expand.
  let value = getPreRenderable(param.value);
  let label = param.id;
  let desc = '';

  // process value according to type is possible and required.
  const appDesc = apps?.find((a) => a.key.name === app.application.name)?.descriptor;
  if (appDesc) {
    for (const paramDesc of appDesc.startCommand.parameters) {
      if (paramDesc.id === param.id) {
        label = paramDesc.name;
        desc = paramDesc.longDescription;
        value = getMaskedPreRenderable(param.value, paramDesc.type);
        break;
      }
    }
  }

  result.push({
    name: label,
    description: desc,
    preview: value,
    link: link,
    group: group,
  });
}

export function gatherPathExpansions(instance: InstanceConfigurationDto): LinkVariable[] {
  const result: LinkVariable[] = [];
  result.push({
    name: 'P:ROOT',
    description: 'The root directory of the instance version installation.',
    preview: `/deploy/${instance.config.id}`,
    link: '{{P:ROOT}}',
    group: null,
  });
  result.push({
    name: 'P:CONFIG',
    description: 'Resolved path to the configuration folder.',
    preview: `/deploy/${instance.config.id}/bin/N/config`,
    link: '{{P:CONFIG}}',
    group: null,
  });
  result.push({
    name: 'P:RUNTIME',
    description: 'The directory where version specific runtime information is stored.',
    preview: `/deploy/${instance.config.id}/bin/N/runtime`,
    link: '{{P:RUNTIME}}',
    group: null,
  });
  result.push({
    name: 'P:BIN',
    description: 'The directory where binaries are installed without pooling (per instance version).',
    preview: `/deploy/${instance.config.id}/bin/N`,
    link: '{{P:BIN}}',
    group: null,
  });
  result.push({
    name: 'P:DATA',
    description: 'The directory where applications can place version independant data.',
    preview: `/deploy/${instance.config.id}/data`,
    link: '{{P:DATA}}',
    group: null,
  });
  result.push({
    name: 'P:MANIFEST_POOL',
    description: 'The directory where globally pooled applications are installed.',
    preview: `/deploy/pool`,
    link: '{{P:MANIFEST_POOL}}',
    group: null,
  });
  result.push({
    name: 'P:INSTANCE_MANIFEST_POOL',
    description: 'The directory where instance-locally pooled applications are installed.',
    preview: `/deploy/${instance.config.id}/pool`,
    link: '{{P:INSTANCE_MANIFEST_POOL}}',
    group: null,
  });
  return result;
}

export function gatherSpecialExpansions(
  instance: InstanceConfigurationDto,
  process: ApplicationConfiguration,
  system: SystemConfiguration,
): LinkVariable[] {
  const result: LinkVariable[] = [];

  result.push({
    name: 'M',
    description: 'The resolved path to a specified manifest.',
    preview: '/deploy/pool/example_1.0.0',
    link: '{{M:<manifest-name>}}',
    group: null,
    matches: (s) => s.startsWith('{{M:') && s.endsWith('}}'),
  });
  result.push({
    name: 'ENV',
    description: 'The resolved environment variable value.',
    preview: '<env-value>',
    link: '{{ENV:<env-name>}}',
    group: null,
    matches: (s) => s.startsWith('{{ENV:') && s.endsWith('}}'),
  });
  result.push({
    name: 'DELAYED',
    description:
      'Delays another link until starting the application (instead of installation), e.g. {{DELAYED:ENV:PATH}}.',
    preview: '<delayed-value>',
    link: '{{DELAYED:<link-expression>}}',
    group: null,
    matches: (s) => s.startsWith('{{DELAYED:') && s.endsWith('}}'),
  });

  result.push({
    name: 'I:SYSTEM_PURPOSE',
    description: `The instance's configured purpose`,
    preview: instance?.config?.purpose,
    link: '{{I:SYSTEM_PURPOSE}}',
    group: null,
  });
  result.push({
    name: 'I:NAME',
    description: `The instance name`,
    preview: instance?.config?.name,
    link: '{{I:NAME}}',
    group: null,
  });
  result.push({
    name: 'I:UUID',
    description: `The instance ID`,
    preview: instance?.config?.id,
    link: '{{I:UUID}}',
    group: null,
  });
  result.push({
    name: 'I:TAG',
    description: 'The instance version',
    preview: 'X',
    link: '{{I:TAG}}',
    group: null,
  });
  result.push({
    name: 'I:PRODUCT_ID',
    description: `The instance's configured product ID`,
    preview: instance?.config?.product?.name,
    link: '{{I:PRODUCT_ID}}',
    group: null,
  });
  result.push({
    name: 'I:PRODUCT_TAG',
    description: `The instance's configured product version`,
    preview: instance?.config?.product?.tag,
    link: '{{I:PRODUCT_TAG}}',
    group: null,
  });
  result.push({
    name: 'I:DEPLOYMENT_INFO_FILE',
    description: `The path to a file containing the instance's serialized deployment configuration.`,
    preview: '/deploy/instance/deployment-info.json',
    link: '{{I:DEPLOYMENT_INFO_FILE}}',
    group: null,
  });
  if (process) {
    result.push({
      name: 'A:NAME',
      description: `The application name`,
      preview: process.name,
      link: '{{A:NAME}}',
      group: null,
    });
    result.push({
      name: 'A:UUID',
      description: `The application ID`,
      preview: process.id,
      link: '{{A:UUID}}',
      group: null,
    });
  }
  result.push({
    name: 'H:HOSTNAME',
    description:
      'The hostname of the machine executing this application. In case of client applications, this is the hostname of the client PC.',
    preview: '<hostname>',
    link: '{{H:HOSTNAME}}',
    group: null,
  });

  result.push({
    name: 'WINDOWS',
    description: 'Expands to the provided value only in case of the target node running Windows.',
    preview: '<windows-value>',
    link: '{{WINDOWS:<windows-value>}}',
    group: null,
    matches: (s) => s.startsWith('{{WINDOWS:'),
    expand: (s) =>
      process
        ? getAppOs(process.application) === OperatingSystem.WINDOWS
          ? s.substring('{{WINDOWS:'.length, s.indexOf('}}'))
          : ''
        : '',
  });

  result.push({
    name: 'LINUX',
    description: 'Expands to the provided value only in case of the target node running Linux.',
    preview: '<linux-value>',
    link: '{{LINUX:<linux-value>}}',
    group: null,
    matches: (s) => s.startsWith('{{LINUX:'),
    expand: (s) =>
      process
        ? getAppOs(process.application) === OperatingSystem.LINUX
          ? s.substring('{{LINUX:'.length, s.indexOf('}}'))
          : ''
        : '',
  });

  result.push({
    name: 'IF',
    description:
      'Conditionally expands to the first (true) or second (false) value depending on the result of the given condition expression.',
    preview: '<result>',
    link: '{{IF:condition?valueIfTrue:valueIfFalse}}',
    group: null,
    matches: (s) => s.startsWith('{{IF:'),
    expand: (s) => expandCondition(s, instance, process, system),
  });

  result.push({
    name: 'XML',
    description: 'Escapes special XML characters',
    preview: '<xml>',
    link: '{{XML:<xml>}}',
    group: null,
    matches: (s) => s.startsWith('{{XML:') && s.endsWith('}}'),
    expand: (s) => expandEscapeSpecialCharacters(SpecialCharacterType.XML, s, instance, process, system),
  });

  result.push({
    name: 'JSON',
    description: 'Escapes special JSON characters',
    preview: '<json>',
    link: '{{JSON:<json>}}',
    group: null,
    matches: (s) => s.startsWith('{{JSON:') && s.endsWith('}}'),
    expand: (s) => expandEscapeSpecialCharacters(SpecialCharacterType.JSON, s, instance, process, system),
  });

  result.push({
    name: 'YAML',
    description: 'Escapes special YAML characters',
    preview: '<yaml>',
    link: '{{YAML:<yaml>}}',
    group: null,
    matches: (s) => s.startsWith('{{YAML:') && s.endsWith('}}'),
    expand: (s) => expandEscapeSpecialCharacters(SpecialCharacterType.YAML, s, instance, process, system),
  });

  return result;
}

function expandEscapeSpecialCharacters(
  type: SpecialCharacterType,
  s: string,
  instance: InstanceConfigurationDto,
  process: ApplicationConfiguration,
  system: SystemConfiguration,
): string {
  const prefix = `{{${type}:`;
  if (!s.startsWith(prefix) || !s.endsWith('}}')) {
    return s; // malformed, don't expand
  }
  const expr = s.substring(prefix.length, s.indexOf('}}'));
  let unescaped = getRenderPreview(createLinkedValue(`{{${expr}}}`), process, instance, system);
  if (unescaped.startsWith('{{') && unescaped.endsWith('}}')) {
    unescaped = unescaped.substring('{{'.length, unescaped.indexOf('}}'));
  }
  return escapeSpecialCharacters(type, unescaped);
}

function expandCondition(
  condition: string,
  instance: InstanceConfigurationDto,
  process: ApplicationConfiguration,
  system: SystemConfiguration,
) {
  const match = condition.match(/{{IF:([^?]*)\?([^:]*):(.*)}}/);

  if (!match) {
    return condition; // no match, expression malformed? just don't expand it...
  }

  const expr = match[1];
  const valIfTrue = match[2];
  const valIfFalse = match[3];

  const exprResult = getRenderPreview(createLinkedValue(`{{${expr}}}`), process, instance, system);

  if (exprResult !== null && exprResult !== undefined && exprResult.trim() !== '' && exprResult.trim() !== 'false') {
    return valIfTrue;
  }
  return valIfFalse;
}
