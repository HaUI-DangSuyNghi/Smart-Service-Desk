import {createExtensionRegistry} from './platform/extensions/registry.ts';
import {registerServiceDesk} from './apps/servicedesk/index.ts';
export const extensions = createExtensionRegistry();
registerServiceDesk(extensions);
