import {cp, mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
const root = resolve(import.meta.dirname, '..');
await mkdir(resolve(root, 'dist'), {recursive: true});
await cp(resolve(root, 'src/platform'), resolve(root, 'dist/platform'), {recursive: true});
console.log('Built standalone platform without application inputs');
