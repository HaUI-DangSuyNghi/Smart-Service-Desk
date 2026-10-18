/** The composition root registers app contributions. Platform owns no domain imports. */
export function createExtensionRegistry() {
  const contributions = new Map();
  const processResolvers = new Map();
  return Object.freeze({
    register(extension) {
      if (!extension?.id || contributions.has(extension.id))
        throw new Error("Duplicate or missing extension ID");
      const resolvers = Object.entries(extension.processResolvers ?? {});
      if (
        resolvers.some(
          ([key, resolve]) =>
            processResolvers.has(key) || typeof resolve !== "function",
        )
      ) {
        throw new Error("Duplicate process resolver or invalid function");
      }
      const contribution = Object.freeze({
        id: extension.id,
        routes: Object.freeze([...(extension.routes ?? [])]),
        menuItems: Object.freeze([...(extension.menuItems ?? [])]),
        dashboardSources: Object.freeze([
          ...(extension.dashboardSources ?? []),
        ]),
        renderers: Object.freeze({ ...extension.renderers }),
      });
      contributions.set(extension.id, contribution);
      for (const [key, resolve] of resolvers)
        processResolvers.set(key, resolve);
      return contribution;
    },
    list() {
      return Object.freeze([...contributions.values()]);
    },
    resolveProcess(processKey, businessKey) {
      return processResolvers.get(processKey)?.(businessKey) ?? null;
    },
  });
}
