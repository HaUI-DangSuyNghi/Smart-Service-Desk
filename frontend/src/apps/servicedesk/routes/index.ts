import type {ExtensionRoute} from '../../../platform/extensions/registry.ts';
export const routes: readonly ExtensionRoute[] = Object.freeze([
  {path: '/servicedesk/tickets', page: 'ticket-list'},
  {path: '/servicedesk/tickets/new', page: 'ticket-create'},
  {path: '/servicedesk/tickets/:id', page: 'ticket-detail'},
  {path: '/servicedesk/statistics', page: 'statistics'},
]);
