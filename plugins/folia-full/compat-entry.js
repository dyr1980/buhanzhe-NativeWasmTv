// Polyfill APIs newer than the minimum WebView 111; native implementations stay in use.
import 'core-js/actual/promise/with-resolvers.js';
import 'core-js/actual/object/group-by.js';
import 'core-js/actual/map/group-by.js';
import 'core-js/actual/array/from-async.js';
import 'core-js/actual/array/to-sorted.js';
import 'core-js/actual/array/to-reversed.js';
import 'core-js/actual/array/to-spliced.js';
import 'core-js/actual/array/with.js';
import 'core-js/actual/string/is-well-formed.js';
import 'core-js/actual/string/to-well-formed.js';
