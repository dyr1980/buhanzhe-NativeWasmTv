// LGPL-2.1-or-later. Avoid allocating a square video framebuffer at EGL startup.
const fs = require('fs');
const path = require('path');
function apply(root) {
    const target = path.join(root, 'ijkmedia/ijksdl/ijksdl_egl.c');
    let source = fs.readFileSync(target, 'utf8').replace(/\r\n/g, '\n');
    const before = 'int32_t height = ANativeWindow_getWidth(window);';
    const after = 'int32_t height = ANativeWindow_getHeight(window);';
    if (!source.includes(after)) {
        if (source.split(before).length !== 2) throw new Error('Unexpected EGL geometry: ' + target);
        source = source.replace(before, after);
    }
    if (!source.includes('#include <android/native_window.h>'))
        source=source.replace('#include "ijksdl_egl.h"','#include "ijksdl_egl.h"\n#ifdef __ANDROID__\n#include <android/native_window.h>\n#endif');
    source=source.replace('IJK_EGL_setSurfaceSize(egl, overlay->w, overlay->h)',
        'IJK_EGL_setSurfaceSize(egl, ANativeWindow_getWidth(egl->window), ANativeWindow_getHeight(egl->window))');
    fs.writeFileSync(target, source);
}
module.exports = apply;
if (require.main === module) apply(process.argv[2]);
