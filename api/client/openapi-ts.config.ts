import { defineConfig } from '@hey-api/openapi-ts';

/**
 * How the wire client is generated from the contract.
 *
 * In a file rather than on the command line because of `module.extension`, which has no flag:
 * every relative import is written with its `.ts` extension, the fully specified form Node's own
 * module loader insists on. A bundler resolves either spelling; Node resolves only this one.
 */
export default defineConfig({
  input: '../openapi/demo.yaml',
  output: {
    path: 'src/gen',
    module: { extension: '.ts' },
  },
});
