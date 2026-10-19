import type {ExtensionRegistry} from '../../platform/extensions/registry.ts';
import {routes} from './routes/index.ts';
export function registerServiceDesk(registry: ExtensionRegistry) {
  return registry.register({
    id: 'servicedesk', routes,
    menuItems: [{label: 'Service Desk', path: '/servicedesk/tickets'}],
    dashboardSources: ['servicedesk.ticket-statistics'],
    renderers: {ticket: 'servicedesk.ticket-detail', suggestion: 'servicedesk.ai-suggestion'},
    processResolvers: {'service-desk': businessKey => `/servicedesk/tickets/${encodeURIComponent(businessKey)}`},
  });
}
