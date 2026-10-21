// Runtime APIs missing from WebView 61; native implementations stay in use.
import 'core-js/stable/index.js';
import 'core-js/actual/structured-clone.js';
import '@formatjs/intl-locale/polyfill.js';
import '@formatjs/intl-segmenter/polyfill.js';
import 'focus-visible';
import './compat-browser.js';
import './compat-style.js';
import {ResizeObserver as ResizeObserverFallback} from '@juggle/resize-observer';
import {AbortController as AbortControllerFallback, AbortSignal as AbortSignalFallback} from 'abort-controller/dist/abort-controller.mjs';
if (typeof globalThis.ResizeObserver === 'undefined') globalThis.ResizeObserver = ResizeObserverFallback;
if (typeof globalThis.AbortController === 'undefined') {
    globalThis.AbortController = AbortControllerFallback;
    globalThis.AbortSignal = AbortSignalFallback;
}
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
