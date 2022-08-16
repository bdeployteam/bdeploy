import {
  ApplicationConfiguration,
  ApplicationDto,
  InstanceConfigurationDto,
  LinkedValueConfiguration,
  OperatingSystem,
  ParameterType,
  SystemConfiguration,
} from 'src/app/models/gen.dtos';
import { getAppOs } from './manifest.utils';

export function createLinkedValue(val: string): LinkedValueConfiguration {
  return !!val && val.indexOf('{{') != -1
    ? { linkExpression: val, value: null }
    : { linkExpression: null, value: val };
}

export function getPreRenderable(val: LinkedValueConfiguration): string {
  return val?.linkExpression ? val?.linkExpression : val?.value;
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
  system: SystemConfiguration
): string {
  if (val.linkExpression) {
    // need to expand. gather all supported expansions for the preview.
    const expansions: LinkVariable[] = [];

    expansions.push(...gatherVariableExpansions(instance, system));
    expansions.push(...gatherProcessExpansions(instance, process));
    expansions.push(...gatherPathExpansions());
    expansions.push(...gatherSpecialExpansions(instance, process));

    // now expand the expression
    return expand(val.linkExpression, expansions);
  }
  return val.value;
}

export function expand(val: string, vars: LinkVariable[]) {
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
  system: SystemConfiguration
): LinkVariable[] {
  const result: LinkVariable[] = [];

  if (instance?.config?.instanceVariables) {
    Object.keys(instance?.config.instanceVariables)
      .map((k) => {
        return {
          name: k,
          description: `Instance Variable - ${instance?.config?.name}`,
          preview: instance.config.instanceVariables[k],
          link: `{{X:${k}}}`,
          group: null,
        };
      })
      .forEach((v) => {
        result.push(v);
      });
  }

  if (system?.configVariables) {
    Object.keys(system.configVariables)
      .map((k) => {
        return {
          name: k,
          description: `System Variable - ${system?.name}`,
          preview: system.configVariables[k],
          link: `{{X:${k}}}`,
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

export function gatherProcessExpansions(
  instance: InstanceConfigurationDto,
  process: ApplicationConfiguration,
  apps: ApplicationDto[] = []
): LinkVariable[] {
  if (!instance?.nodeDtos?.length) {
    return [];
  }

  const result: LinkVariable[] = [];
  for (const node of instance.nodeDtos) {
    for (const app of node.nodeConfiguration.applications) {
      for (const param of app.start.parameters) {
        let link = `{{V:${app.name}:${param.uid}}}`;
        let group = app.name;
        if (app.name === process.name) {
          link = `{{V:${param.uid}}}`;
          group = `${app.name} (This Application)`;
        }

        // just for display - this is OK as value and expression, no need to expand.
        let value = getPreRenderable(param.value);
        let label = param.uid;
        let desc = '';

        // mask out password in case it is one.
        const appDesc = apps?.find(
          (a) => a.key.name === app.application.name
        )?.descriptor;
        if (appDesc) {
          for (const paramDesc of appDesc.startCommand.parameters) {
            if (paramDesc.uid === param.uid) {
              label = paramDesc.name;
              desc = paramDesc.longDescription;
              if (paramDesc.type === ParameterType.PASSWORD) {
                value = '*'.repeat(value.length);
              }
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
    }
  }
  return result;
}

export function gatherPathExpansions(): LinkVariable[] {
  const result: LinkVariable[] = [];
  result.push({
    name: 'P:ROOT',
    description: 'The root directory of the instance version installation.',
    preview: '/deploy/instance',
    link: '{{P:ROOT}}',
    group: null,
  });
  result.push({
    name: 'P:CONFIG',
    description: 'Resolved path to the configuration folder.',
    preview: '/deploy/instance/bin/1/config',
    link: '{{P:CONFIG}}',
    group: null,
  });
  result.push({
    name: 'P:RUNTIME',
    description:
      'The directory where version specific runtime information is stored.',
    preview: '/deploy/instance/bin/1/runtime',
    link: '{{P:RUNTIME}}',
    group: null,
  });
  result.push({
    name: 'P:BIN',
    description:
      'The directory where binaries are installed without pooling (per instance version).',
    preview: '/deploy/instance/bin/1',
    link: '{{P:BIN}}',
    group: null,
  });
  result.push({
    name: 'P:DATA',
    description:
      'The directory where applications can place version independant data.',
    preview: '/deploy/instance/data',
    link: '{{P:DATA}}',
    group: null,
  });
  result.push({
    name: 'P:MANIFEST_POOL',
    description:
      'The directory where globally pooled applications are installed.',
    preview: '/deploy/pool',
    link: '{{P:MANIFEST_POOL}}',
    group: null,
  });
  result.push({
    name: 'P:INSTANCE_MANIFEST_POOL',
    description:
      'The directory where instance-locally pooled applications are installed.',
    preview: '/deploy/instance/pool',
    link: '{{P:INSTANCE_MANIFEST_POOL}}',
    group: null,
  });
  return result;
}

export function gatherSpecialExpansions(
  instance: InstanceConfigurationDto,
  process: ApplicationConfiguration
): LinkVariable[] {
  const result: LinkVariable[] = [];

  result.push({
    name: 'M',
    description: 'The resolved path to a specified manifest.',
    preview: '/deploy/pool/example_1.0.0',
    link: '{{M:<manifest-name>}}',
    group: null,
  });
  result.push({
    name: 'ENV',
    description: 'The resolved environment variable value.',
    preview: '<env-value>',
    link: '{{ENV:<env-name>}}',
    group: null,
  });
  result.push({
    name: 'DELAYED',
    description:
      'Delays another link until starting the application (instead of installation), e.g. {{DELAYED:ENV:PATH}}.',
    preview: '<delayed-value>',
    link: '{{DELAYED:<link-expression>}}',
    group: null,
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
    preview: instance?.config?.uuid,
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
  result.push({
    name: 'A:NAME',
    description: `The application name`,
    preview: process?.name,
    link: '{{A:NAME}}',
    group: null,
  });
  result.push({
    name: 'A:UUID',
    description: `The application ID`,
    preview: process?.uid,
    link: '{{A:UUID}}',
    group: null,
  });
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
    description:
      'Expands to the provided value only in case of the target node running Windows.',
    preview: '<windows-value>',
    link: '{{WINDOWS:<windows-value>}}',
    group: null,
    matches: (s) => s.startsWith('{{WINDOWS:'),
    expand: (s) =>
      getAppOs(process.application) === OperatingSystem.WINDOWS
        ? s.substring('{{WINDOWS:'.length, s.indexOf('}}'))
        : '',
  });

  result.push({
    name: 'LINUX',
    description:
      'Expands to the provided value only in case of the target node running Linux.',
    preview: '<linux-value>',
    link: '{{LINUX:<linux-value>}}',
    group: null,
    matches: (s) => s.startsWith('{{LINUX:'),
    expand: (s) =>
      getAppOs(process.application) === OperatingSystem.LINUX
        ? s.substring('{{LINUX:'.length, s.indexOf('}}'))
        : '',
  });

  return result;
}
