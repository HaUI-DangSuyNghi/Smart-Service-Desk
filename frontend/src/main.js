import {createExtensionRegistry} from './platform/extensions/registry.js';
import {registerServiceDesk} from './apps/servicedesk/index.js';
export const extensions = createExtensionRegistry();
registerServiceDesk(extensions);
