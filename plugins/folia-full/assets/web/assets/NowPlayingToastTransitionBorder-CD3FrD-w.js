System.register(["./preload-helper-CjjX_lzZ.js","./lucide-icons-gYazRYpo.js","./noLyricLines-CG7Vr8i2.js","./proxy-10nj9KHE.js","./animate-BL0AH54s.js","./playerOverlayLayout-DXXlkTiq.js","./use-motion-value-xXQHLoxj.js"],function(_export,_context){"use strict";var e,t,n,r,i,a,o,s,c,l,u,d,f,p,m,h,g,_,v,y,b,x,S,C,w,T,E,D,O,k,A,j,M,N,P,F,I,L;return{setters:[function(_preloadHelperCjjX_lzZJs){e=_preloadHelperCjjX_lzZJs.i;},function(_lucideIconsGYazRYpoJs){t=_lucideIconsGYazRYpoJs.Ps;},function(_noLyricLinesCG7Vr8i2Js){n=_noLyricLinesCG7Vr8i2Js.Dr;r=_noLyricLinesCG7Vr8i2Js.Hr;i=_noLyricLinesCG7Vr8i2Js.Jr;a=_noLyricLinesCG7Vr8i2Js.Kr;o=_noLyricLinesCG7Vr8i2Js.Qi;s=_noLyricLinesCG7Vr8i2Js.qr;},function(_proxy10nj9KHEJs){c=_proxy10nj9KHEJs.t;},function(_animateBL0AH54sJs){l=_animateBL0AH54sJs.t;},function(_playerOverlayLayoutDXXlkTiqJs){u=_playerOverlayLayoutDXXlkTiqJs.$;d=_playerOverlayLayoutDXXlkTiqJs.K;f=_playerOverlayLayoutDXXlkTiqJs.Q;p=_playerOverlayLayoutDXXlkTiqJs.X;m=_playerOverlayLayoutDXXlkTiqJs.Z;h=_playerOverlayLayoutDXXlkTiqJs.et;g=_playerOverlayLayoutDXXlkTiqJs.nt;_=_playerOverlayLayoutDXXlkTiqJs.tt;},function(_useMotionValueXXQHLoxjJs){v=_useMotionValueXXQHLoxjJs.t;}],execute:function(){y={maxColorCount:5,maxSpots:4};`${y.maxColorCount}${h}${_}${y.maxColorCount}${y.maxSpots}${u}`;b=e(t(),1),x=`#version 300 es
precision lowp float;

uniform float u_time;

uniform vec4 u_colorBack;
uniform vec4 u_colorTrack;
uniform vec4 u_colorBase;
uniform vec4 u_colors[${y.maxColorCount}];
uniform float u_colorsCount;
uniform float u_roundness;
uniform float u_thickness;
uniform float u_marginLeft;
uniform float u_marginRight;
uniform float u_marginTop;
uniform float u_marginBottom;
uniform float u_softness;
uniform float u_intensity;
uniform float u_bloom;
uniform float u_spotSize;
uniform float u_spots;
uniform float u_pulse;
uniform float u_smoke;
uniform float u_smokeSize;
uniform float u_progress;
uniform float u_progressFade;
uniform float u_progressHead;

uniform sampler2D u_noiseTexture;

in vec2 v_responsiveUV;
in vec2 v_responsiveBoxGivenSize;
in vec2 v_patternUV;

out vec4 fragColor;

#define TWO_PI 6.28318530718
#define PI 3.14159265358979323846

float beat(float time) {
  float first = pow(abs(sin(time * TWO_PI)), 10.);
  float second = pow(abs(sin((time - .15) * TWO_PI)), 10.);

  return clamp(first + 0.6 * second, 0.0, 1.0);
}

float sst(float edge0, float edge1, float x) {
  return smoothstep(edge0, edge1, x);
}

float roundedBox(vec2 uv, vec2 halfSize, float distance, float cornerDistance, float thickness, float softness) {
  float borderDistance = abs(distance);
  float aa = 2. * fwidth(distance);
  float border = 1. - sst(min(mix(thickness, -thickness, softness), thickness + aa), max(mix(thickness, -thickness, softness), thickness + aa), borderDistance);
  float cornerFadeCircles = 0.;
  cornerFadeCircles = mix(1., cornerFadeCircles, sst(0., 1., length((uv + halfSize) / thickness)));
  cornerFadeCircles = mix(1., cornerFadeCircles, sst(0., 1., length((uv - vec2(-halfSize.x, halfSize.y)) / thickness)));
  cornerFadeCircles = mix(1., cornerFadeCircles, sst(0., 1., length((uv - vec2(halfSize.x, -halfSize.y)) / thickness)));
  cornerFadeCircles = mix(1., cornerFadeCircles, sst(0., 1., length((uv - halfSize) / thickness)));
  aa = fwidth(cornerDistance);
  float cornerFade = sst(0., mix(aa, thickness, softness), cornerDistance);
  cornerFade *= cornerFadeCircles;
  border += cornerFade;
  return border;
}

/**
 * 把边框附近的点映射成「从 12 点方向顺时针走过的周长」，单位与 UV 一致。
 * 圆角矩形拆成 4 段直边 + 4 段圆角弧逐段累加，所以长边和圆角上的推进速度一致；
 * 圆角吃满时退化成圆，等价于极角。out 参数返回整圈周长，调用方用它归一化。
 */
float perimeterCoord(vec2 p, vec2 halfSize, float radius, out float perimeter) {
  float rx = max(halfSize.x - radius, 0.);
  float ry = max(halfSize.y - radius, 0.);
  float arc = .5 * PI * radius;
  float edgeX = 2. * rx;
  float edgeY = 2. * ry;
  perimeter = 2. * (edgeX + edgeY) + 4. * arc;

  float c0 = rx;
  float c1 = c0 + arc;
  float c2 = c1 + edgeY;
  float c3 = c2 + arc;
  float c4 = c3 + edgeX;
  float c5 = c4 + arc;
  float c6 = c5 + edgeY;
  float c7 = c6 + arc;

  vec2 q = abs(p) - vec2(rx, ry);

  if (q.x > 0. && q.y > 0.) {
    if (p.x >= 0. && p.y >= 0.) return c0 + radius * atan(q.x, q.y);
    if (p.x >= 0.) return c2 + radius * atan(q.y, q.x);
    if (p.y < 0.) return c4 + radius * atan(q.x, q.y);
    return c6 + radius * atan(q.y, q.x);
  }
  if (q.x > q.y) {
    return (p.x >= 0.) ? c1 + (ry - p.y) : c5 + (p.y + ry);
  }
  if (p.y < 0.) return c3 + (rx - p.x);
  return (p.x >= 0.) ? p.x : c7 + (p.x + rx);
}

vec2 randomGB(vec2 p) {
  vec2 uv = floor(p) / 100. + .5;
  return texture(u_noiseTexture, fract(uv)).gb;
}

float randomG(vec2 p) {
  vec2 uv = floor(p) / 100. + .5;
  return texture(u_noiseTexture, fract(uv)).g;
}

float valueNoise(vec2 st) {
  vec2 i = floor(st);
  vec2 f = fract(st);
  float a = randomG(i);
  float b = randomG(i + vec2(1.0, 0.0));
  float c = randomG(i + vec2(0.0, 1.0));
  float d = randomG(i + vec2(1.0, 1.0));
  vec2 u = f * f * (3.0 - 2.0 * f);
  float x1 = mix(a, b, u.x);
  float x2 = mix(c, d, u.x);
  return mix(x1, x2, u.y);
}

void main() {
  const float firstFrameOffset = 109.;
  float t = 1.2 * (u_time + firstFrameOffset);

  vec2 borderUV = v_responsiveUV;
  float pulse = u_pulse * beat(.18 * u_time);

  float canvasRatio = v_responsiveBoxGivenSize.x / v_responsiveBoxGivenSize.y;
  vec2 halfSize = vec2(.5);
  borderUV.x *= max(canvasRatio, 1.);
  borderUV.y /= min(canvasRatio, 1.);
  halfSize.x *= max(canvasRatio, 1.);
  halfSize.y /= min(canvasRatio, 1.);

  float mL = u_marginLeft;
  float mR = u_marginRight;
  float mT = u_marginTop;
  float mB = u_marginBottom;
  float mX = mL + mR;
  float mY = mT + mB;

  float thickness = .5 * u_thickness * min(halfSize.x, halfSize.y);

  halfSize.x *= (1. - mX);
  halfSize.y *= (1. - mY);

  vec2 centerShift = vec2(
  (mL - mR) * max(canvasRatio, 1.) * 0.5,
  (mB - mT) / min(canvasRatio, 1.) * 0.5
  );

  borderUV -= centerShift;
  halfSize -= mix(thickness, 0., u_softness);

  float radius = mix(0., min(halfSize.x, halfSize.y), u_roundness);
  vec2 d = abs(borderUV) - halfSize + radius;
  float outsideDistance = length(max(d, .0001)) - radius;
  float insideDistance = min(max(d.x, d.y), .0001);
  float cornerDistance = abs(min(max(d.x, d.y) - .45 * radius, .0));
  float distance = outsideDistance + insideDistance;

  float borderThickness = mix(thickness, 3. * thickness, u_softness);
  float border = roundedBox(borderUV, halfSize, distance, cornerDistance, borderThickness, u_softness);
  border = pow(border, 1. + u_softness);

  vec2 smokeUV = .3 * u_smokeSize * v_patternUV;
  float smoke = clamp(3. * valueNoise(2.7 * smokeUV + .5 * t), 0., 1.);
  smoke -= valueNoise(3.4 * smokeUV - .5 * t);
  float smokeThickness = thickness + .2;
  smokeThickness = min(.4, max(smokeThickness, .1));
  smoke *= roundedBox(borderUV, halfSize, distance, cornerDistance, smokeThickness, 1.);
  smoke = 30. * smoke * smoke;
  smoke *= mix(0., .5, pow(u_smoke, 2.));
  smoke *= mix(1., pulse, u_pulse);
  smoke = clamp(smoke, 0., 1.);
  border += smoke;

  border = clamp(border, 0., 1.);

  // 沿周长的位置，替代原版的极角：进度和光斑都按弧长走
  float perimeter;
  float travelled = perimeterCoord(borderUV, halfSize, radius, perimeter);
  perimeter = max(perimeter, 1e-6);
  float angle = travelled / perimeter;

  // 已完成弧段的遮罩：弧段外的像素按「到端点的周长距离」羽化，
  // 羽化距离与描边宽度同量级时，端点看起来是个圆头，而不是一刀切
  float progress = clamp(u_progress, 0., 1.);
  float headPosition = progress * perimeter;
  float outsideArc = (travelled <= headPosition) ? 0. : min(travelled - headPosition, perimeter - travelled);
  float fadeDistance = max(u_progressFade * perimeter, 1e-5);
  float progressMask = 1. - sst(0., fadeDistance, outsideArc);
  progressMask = mix(progressMask, 1., step(1., progress));
  progressMask *= sst(0., .004, progress);

  float shape = border;
  border *= progressMask;

  // 常亮描边：不带柔边的窄环，用来垫轨道和已完成段的底色
  float solidStroke = roundedBox(borderUV, halfSize, distance, cornerDistance, max(thickness, .002), 0.);

  vec3 trackPremul = u_colorTrack.rgb * u_colorTrack.a * solidStroke;
  float trackAlpha = u_colorTrack.a * solidStroke;
  vec3 basePremul = u_colorBase.rgb * u_colorBase.a * solidStroke * progressMask;
  float baseAlpha = u_colorBase.a * solidStroke * progressMask;

  vec3 blendColor = trackPremul + (1. - trackAlpha) * basePremul;
  float blendAlpha = trackAlpha + (1. - trackAlpha) * baseAlpha;
  vec3 addColor = blendColor;
  float addAlpha = blendAlpha;

  float bloom = 4. * u_bloom;
  float intensity = 1. + (1. + 4. * u_softness) * u_intensity;

  for (int colorIdx = 0; colorIdx < ${y.maxColorCount}; colorIdx++) {
    if (colorIdx >= int(u_colorsCount)) break;
    float colorIdxF = float(colorIdx);

    vec3 c = u_colors[colorIdx].rgb * u_colors[colorIdx].a;
    float a = u_colors[colorIdx].a;

    for (int spotIdx = 0; spotIdx < ${y.maxSpots}; spotIdx++) {
      if (spotIdx >= int(u_spots)) break;
      float spotIdxF = float(spotIdx);

      vec2 randVal = randomGB(vec2(spotIdxF * 10. + 2., 40. + colorIdxF));

      float time = (.1 + .15 * abs(sin(spotIdxF * (2. + colorIdxF)) * cos(spotIdxF * (2. + 2.5 * colorIdxF)))) * t + randVal.x * 3.;
      time *= mix(1., -1., step(.5, randVal.y));

      float mask = .5 + .5 * mix(
      sin(t + spotIdxF * (5. - 1.5 * colorIdxF)),
      cos(t + spotIdxF * (3. + 1.3 * colorIdxF)),
      step(mod(colorIdxF, 2.), .5)
      );

      float p = clamp(2. * u_pulse - randVal.x, 0., 1.);
      mask = mix(mask, pulse, p);

      float atg1 = fract(angle + time);
      float spotSize = .05 + .6 * pow(u_spotSize, 2.) + .05 * randVal.x;
      spotSize = mix(spotSize, .1, p);
      float sector = sst(.5 - spotSize, .5, atg1) * (1. - sst(.5, .5 + spotSize, atg1));

      sector *= mask;
      sector *= border;
      sector *= intensity;
      sector = clamp(sector, 0., 1.);

      vec3 srcColor = c * sector;
      float srcAlpha = a * sector;

      blendColor += ((1. - blendAlpha) * srcColor);
      blendAlpha = blendAlpha + (1. - blendAlpha) * srcAlpha;
      addColor += srcColor;
      addAlpha += srcAlpha;
    }
  }

  vec3 accumColor = mix(blendColor, addColor, bloom);
  float accumAlpha = mix(blendAlpha, addAlpha, bloom);
  accumAlpha = clamp(accumAlpha, 0., 1.);

  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
  vec3 color = accumColor + (1. - accumAlpha) * bgColor;
  float opacity = accumAlpha + (1. - accumAlpha) * u_colorBack.a;

  // 端点高光：贴着描边的一小段白热点，标出进度尖端；跑满和归零时收掉
  float headDistance = abs(travelled - headPosition);
  headDistance = min(headDistance, perimeter - headDistance);
  float head = u_progressHead * shape * (1. - sst(0., 2. * borderThickness, headDistance));
  head *= sst(0., .01, progress) * (1. - step(.999, progress));
  color += head;
  opacity = clamp(opacity + head, 0., 1.);

  color += 1. / 256. * (fract(sin(dot(.014 * gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453123) - .5);

  fragColor = vec4(color, opacity);
}`,S=(e,t)=>{let[n,r,i,a]=f(e);return[n,r,i,a*t];},C=e=>({u_colorBack:f(e.colorBack),u_colorTrack:S(e.colorTrack,e.trackOpacity),u_colorBase:S(e.colorBase,e.baseOpacity),u_colors:e.colors.map(f),u_colorsCount:e.colors.length,u_roundness:e.roundness,u_thickness:e.thickness,u_marginLeft:e.marginX,u_marginRight:e.marginX,u_marginTop:e.marginY,u_marginBottom:e.marginY,u_softness:e.softness,u_intensity:e.intensity,u_bloom:e.bloom,u_spots:e.spots,u_spotSize:e.spotSize,u_pulse:e.pulse,u_smoke:e.smoke,u_smokeSize:e.smokeSize,u_progress:e.progress,u_progressFade:e.progressFade,u_progressHead:e.progressHead,u_noiseTexture:m(),u_fit:g.contain,u_rotation:0,u_scale:1,u_offsetX:0,u_offsetY:0,u_originX:.5,u_originY:.5,u_worldWidth:0,u_worldHeight:0}),w=o(),T={speed:1,thickness:.015,softness:.7,intensity:.35,bloom:.6,spots:3,spotSize:.35,pulse:.25,smoke:.55,fade:.02,head:.55,base:.4,track:.14},E=[`#0dc1fd`,`#d915ef`,`#ff8a3d`],D=e=>Math.min(1,Math.max(0,e/100)),O=(e,t,n,r)=>{let i=Math.max(e/2-n-r,0),a=Math.max(t/2-n-r,0);return Math.max(1,4*(i+a)+2*Math.PI*r);},k=({progress:e,width:t,height:n,pad:r=24,cornerRadius:i,colors:a=E,trackColor:o=`#ffffff`,baseColor:s,tuning:c,minPixelRatio:l,maxPixelCount:u,className:f,style:m})=>{let h=(0,b.useRef)(null),g=(0,b.useRef)(D(e.get())),_=(0,b.useRef)(g.current),v={...T,...c},y=Math.max(1,Math.min(t,n)/2-r),S=i==null?y:i,k=O(t,n,r,S),A=v.speed!==0;(0,b.useLayoutEffect)(()=>{let t=.5/k,n=e=>{var n;let r=D(e);if(g.current=r,!(r<=0||r>=1)&&Math.abs(r-_.current)<t)return;_.current=r;let i=(n=h.current)==null?void 0:n.paperShaderMount;i&&d(i,{u_progress:r},A);};return _.current=D(e.get()),n(e.get()),e.on(`change`,n);},[e,k,A]);let j=C({colors:a,colorBack:"rgba(0,0,0,0)",colorTrack:o,trackOpacity:v.track,colorBase:s==null?a[0]:s,baseOpacity:v.base,roundness:Math.min(1,Math.max(0,S/y)),thickness:v.thickness,marginX:r/t,marginY:r/n,softness:v.softness,intensity:v.intensity,bloom:v.bloom,spots:v.spots,spotSize:v.spotSize,pulse:v.pulse,smoke:v.smoke,smokeSize:0,progress:g.current,progressFade:v.fade,progressHead:v.head});return(0,w.jsx)(p,{ref:h,className:f,fragmentShader:x,uniforms:j,speed:v.speed,minPixelRatio:l,maxPixelCount:u,width:t,height:n,style:{width:t,height:n,...m}});},A=0,j=.45,M={thickness:.032,intensity:.3,bloom:.45,head:.5,base:.72,track:.2},N=16,P=.07,F=.12,I=e=>{let t=a(e);if(!t)return[e,e,e];let n=(e,n,a)=>s({h:i(t.h+e),s:r(t.s+a),l:r(t.l+n)});return[n(-16,P,-.12),e,n(N,-.042,F)];},_export("default",L=({cue:e,startAtMs:t=0,cardWidth:r,cardHeight:i,cardRadius:a,isDaylight:o,theme:s})=>{let u=v(0);(0,b.useEffect)(()=>{let n=e.seconds*1e3,r=Math.min(100,Math.max(0,t/n*100)),i=Math.max(0,(n-t)/1e3);if(u.set(r),i<=0)return;let a=l(u,100,{duration:i,ease:`linear`});return()=>a.stop();},[e,t,u]);let d=r+44,f=i+44,p=n(`transitionOverlay`),m=(s==null?void 0:s.accentColor)||(o?`#27272a`:`#fafafa`);return(0,w.jsx)(c.div,{"aria-hidden":!0,initial:{opacity:0},animate:{opacity:1},exit:{opacity:0,transition:{duration:.5,ease:`easeOut`}},transition:{duration:j,ease:`easeOut`},className:`pointer-events-none`,style:{position:`absolute`,top:-22,left:-22,width:d,height:f},children:(0,w.jsx)(k,{className:`pointer-events-none absolute inset-0`,progress:u,width:d,height:f,pad:22,cornerRadius:a+A,colors:I(m),baseColor:m,trackColor:o?`#000000`:`#ffffff`,tuning:p?{...M,speed:0,pulse:0,smoke:0}:M})});});}};});