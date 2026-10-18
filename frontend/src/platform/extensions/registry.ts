export interface ExtensionRoute { readonly path: string; readonly page: string }
export interface MenuItem { readonly label: string; readonly path: string }
export interface ExtensionContribution {
  readonly id: string;
  readonly routes?: readonly ExtensionRoute[];
  readonly menuItems?: readonly MenuItem[];
  readonly dashboardSources?: readonly string[];
  readonly renderers?: Readonly<Record<string, string>>;
  readonly processResolvers?: Readonly<Record<string, (businessKey: string) => string>>;
}
export interface RegisteredExtension {
  readonly id: string;
  readonly routes: readonly ExtensionRoute[];
  readonly menuItems: readonly MenuItem[];
  readonly dashboardSources: readonly string[];
  readonly renderers: Readonly<Record<string, string>>;
}
export interface ExtensionRegistry {
  register(extension: ExtensionContribution): RegisteredExtension;
  list(): readonly RegisteredExtension[];
  resolveProcess(processKey: string, businessKey: string): string | null;
}

/** Applications register contributions at the composition root. */
export function createExtensionRegistry(): ExtensionRegistry {
  const contributions = new Map<string, RegisteredExtension>();
  const processResolvers = new Map<string, (businessKey: string) => string>();
  return Object.freeze({
    register(extension: ExtensionContribution): RegisteredExtension {
      if (!extension?.id || contributions.has(extension.id)) throw new Error('Duplicate or missing extension ID');
      const resolvers = Object.entries(extension.processResolvers ?? {});
      if (resolvers.some(([key, resolve]) => processResolvers.has(key) || typeof resolve !== 'function')) {
        throw new Error('Duplicate process resolver or invalid function');
      }
      const contribution: RegisteredExtension = Object.freeze({
        id: extension.id,
        routes: Object.freeze((extension.routes ?? []).map(route => Object.freeze({...route}))),
        menuItems: Object.freeze((extension.menuItems ?? []).map(item => Object.freeze({...item}))),
        dashboardSources: Object.freeze([...(extension.dashboardSources ?? [])]),
        renderers: Object.freeze({...extension.renderers}),
      });
      contributions.set(extension.id, contribution);
      for (const [key, resolve] of resolvers) processResolvers.set(key, resolve);
      return contribution;
    },
    list: () => Object.freeze([...contributions.values()]),
    resolveProcess: (processKey: string, businessKey: string) => processResolvers.get(processKey)?.(businessKey) ?? null,
  });
}
