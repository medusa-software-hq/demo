import type { TestEnvironment } from './environment.ts';
import { startLocalEnvironment } from './local/localEnvironment.ts';
import { remoteEnvironment } from './remote/remoteEnvironment.ts';
import { countersSuite } from './suites/counters.ts';
import { todosSuite } from './suites/todos.ts';
import { wayInSuite } from './suites/wayIn.ts';
import { workSuite } from './suites/work.ts';
import { after } from 'node:test';

/**
 * The system, tested from outside, through the way in a browser takes.
 *
 * By default, against a stack started here: the Service and a worker as they run anywhere, the
 * Worker in front of them as it is deployed, and the login in front of that played by the tests.
 *
 * With `SYSTEM_TESTS_ORIGIN` set, against the deployed app at that origin instead — past its real
 * login, as the two service tokens in `SYSTEM_TESTS_SERVICE_TOKENS`. That is where what cannot be
 * started here gets tested: the permissions a project was given, the login's own configuration,
 * and everything else that exists only once something has been deployed.
 *
 * The suites cannot tell which of the two they run against, except where a test says it needs the
 * login to be played here.
 */

const origin = process.env['SYSTEM_TESTS_ORIGIN'];

const environment: TestEnvironment =
  origin === undefined || origin === ''
    ? await startLocalEnvironment()
    : remoteEnvironment(origin, process.env['SYSTEM_TESTS_SERVICE_TOKENS'] ?? '');

after(() => environment.stop());

countersSuite(environment);
todosSuite(environment);
workSuite(environment);
wayInSuite(environment);
