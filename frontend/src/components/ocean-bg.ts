import { BaseElement, define } from './base'
import type { Theme, EffectiveTheme } from './types'

/** 프래그먼트 셰이더 — 하늘·구름·태양·파도·반사·별을 한 번에 그립니다. */
const FRAGMENT_SHADER =
  '\nprecision highp float;\nuniform vec2 R; uniform float T; uniform float WAVES; uniform float SUNH; uniform float DAY; uniform float TH;\nfloat dayW(){ return clamp(1.0-TH, 0.0, 1.0); }\nfloat nightW(){ return clamp(TH-1.0, 0.0, 1.0); }\nvec3 pal(vec3 d, vec3 s, vec3 n){ return mix(mix(d, s, clamp(TH,0.0,1.0)), n, clamp(TH-1.0,0.0,1.0)); }\n\nfloat hash(vec2 p){ p = mod(p, 256.0); return fract(sin(dot(p, vec2(127.1,311.7)))*43758.5453123); }\nfloat noise(vec2 p){\n  p = mod(p, 256.0);\n  vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);\n  float a=hash(i), b=hash(i+vec2(1.,0.)), c=hash(i+vec2(0.,1.)), d=hash(i+vec2(1.,1.));\n  return mix(mix(a,b,f.x), mix(c,d,f.x), f.y);\n}\nfloat fbm(vec2 p){ float v=0., a=.5; for(int i=0;i<4;i++){ v+=a*noise(p); p=p*2.03+vec2(1.7,9.2); a*=.5; } return v; }\nfloat fbm3(vec2 p){ float v=0., a=.5; for(int i=0;i<3;i++){ v+=a*noise(p); p=p*2.07+vec2(3.1,1.7); a*=.5; } return v; }\n\nconst float HZ = 0.475;\nfloat sunX(){ return mix(0.13, 0.90, DAY); }\n// 지평선 아래에서 떠올라 호를 그리고 다시 잠긴다 (루프 이음매는 물속에 숨는다)\nfloat sunY(){ float lift = dayW()*0.26 + nightW()*0.17; return HZ + 0.004 + 0.112*pow(sin(3.14159265*DAY), 0.62) + (SUNH-0.5)*0.030 + lift; }\nfloat sunLow(){ return smoothstep(0.085, -0.030, sunY()-HZ); }\n\nvec3 skyCol(vec2 uv){\n  float h = clamp((uv.y-HZ)/(1.0-HZ), 0.0, 1.4);\n  float w = sunLow();\n  vec3 cTop = pal(vec3(0.25,0.47,0.78), mix(vec3(0.36,0.33,0.64), vec3(0.26,0.22,0.52), w), vec3(0.105,0.130,0.290));\n  vec3 cMid = pal(vec3(0.52,0.73,0.91), mix(vec3(0.84,0.48,0.68), vec3(0.74,0.38,0.62), w), vec3(0.180,0.215,0.420));\n  vec3 cLow = pal(vec3(0.80,0.90,0.97), mix(vec3(1.00,0.74,0.74), vec3(0.99,0.58,0.62), w), vec3(0.300,0.320,0.530));\n  vec3 c = mix(cMid, cTop, smoothstep(0.20,0.95,h));\n  c = mix(cLow, c, smoothstep(0.0,0.26,h));\n  vec2 sp = vec2(sunX(), sunY());\n  float ar = R.x/R.y;\n  float r  = length((uv-sp)*vec2(ar,1.0));        // 원반: 정원\n  float rg = length((uv-sp)*vec2(ar,1.55));      // 광채: 살짝 납작하게\n    vec3 glowC = pal(vec3(1.0,0.97,0.90), mix(vec3(1.0,0.88,0.84), vec3(1.0,0.68,0.60), w), vec3(0.70,0.78,0.98));\n  float gN = nightW();\n  c += glowC*(exp(-rg*13.0)*mix(0.34,0.20,gN) + exp(-rg*34.0)*mix(0.55,0.42,gN));\n  c += pal(vec3(1.0,1.0,0.96), vec3(1.0,0.97,0.94), vec3(0.93,0.96,1.0))*smoothstep(0.016,0.004,r)*mix(0.9,0.75,gN);\n  c += vec3(1.0,0.82,0.80)*exp(-abs(uv.y-sp.y)*34.0)*exp(-abs(uv.x-sp.x)*3.4)*0.20*(1.0-gN)*(1.0-dayW()*0.7);\n\n  vec2 cu = vec2(uv.x*1.9 + T*0.022, (uv.y-HZ)*4.2 + T*0.003);\n  float ci = fbm(cu*vec2(1.0,3.0) + fbm3(cu*0.7)*0.9);\n  float cirrus = smoothstep(0.44,0.74, ci)*smoothstep(0.03,0.34,h)*smoothstep(1.30,0.50,h);\n  c = mix(c, mix(vec3(1.0,0.86,0.90), vec3(0.98,0.60,0.74), h), cirrus*0.78);\n\n  vec2 lu = vec2(uv.x*3.0 + T*0.030, (uv.y-HZ)*15.0);\n  float lo = fbm(lu + fbm3(lu*0.6)*0.7);\n  float cum = smoothstep(0.44,0.70, lo)*exp(-abs(uv.y-(HZ+0.050))*22.0);\n  c = mix(c, mix(vec3(0.34,0.21,0.40), vec3(0.84,0.46,0.60), smoothstep(0.1,0.65,lo)), clamp(cum*1.3,0.0,0.94));\n\n  vec2 mu = vec2(uv.x*2.2 + T*0.048, (uv.y-HZ)*9.0);\n  float mo = fbm(mu + fbm3(mu*0.8)*0.9);\n  float md = smoothstep(0.48,0.76, mo)*exp(-abs(uv.y-(HZ+0.115))*13.0);\n  c = mix(c, mix(vec3(0.38,0.24,0.45), vec3(0.90,0.54,0.68), smoothstep(0.2,0.75,mo)), clamp(md*1.1,0.0,0.88));\n\n  vec2 bu = vec2(uv.x*1.3 + T*0.014, uv.y*2.1 + T*0.0015);\n  float bg = fbm(bu + fbm3(bu*0.5)*1.0);\n  float big = smoothstep(0.44,0.76,bg)*smoothstep(0.58,1.0,h)*smoothstep(0.80,0.10,uv.x);\n  c = mix(c, mix(vec3(0.35,0.23,0.44), vec3(0.80,0.45,0.61), bg), big*0.8);\n\n  // 별 — 밤에만\n  float nw = nightW();\n  vec2 g = uv*vec2(ar,1.0)*170.0;\n  vec2 gi = floor(g);\n  float rnd = hash(gi);\n  float d2 = length(fract(g)-0.5);\n  float tw = 0.55 + 0.45*sin(T*1.7 + rnd*47.0);\n  c += vec3(0.88,0.93,1.0)*step(0.9865, rnd)*smoothstep(0.34,0.0,d2)*tw*nw*smoothstep(0.03,0.40,h);\n  return c;\n}\n\nvec3 skyLite(vec2 uv){\n  float h = clamp((uv.y-HZ)/(1.0-HZ), 0.0, 1.4);\n  float w = sunLow();\n  vec3 cTop = pal(vec3(0.25,0.47,0.78), mix(vec3(0.36,0.33,0.64), vec3(0.26,0.22,0.52), w), vec3(0.105,0.130,0.290));\n  vec3 cMid = pal(vec3(0.52,0.73,0.91), mix(vec3(0.84,0.48,0.68), vec3(0.74,0.38,0.62), w), vec3(0.180,0.215,0.420));\n  vec3 cLow = pal(vec3(0.80,0.90,0.97), mix(vec3(1.00,0.74,0.74), vec3(0.99,0.58,0.62), w), vec3(0.300,0.320,0.530));\n  vec3 c = mix(cMid, cTop, smoothstep(0.20,0.95,h));\n  c = mix(cLow, c, smoothstep(0.0,0.26,h));\n  vec2 sp = vec2(sunX(), sunY());\n  float ar = R.x/R.y;\n  float r  = length((uv-sp)*vec2(ar,1.0));        // 원반: 정원\n  float rg = length((uv-sp)*vec2(ar,1.55));      // 광채: 살짝 납작하게\n    vec3 glowC = pal(vec3(1.0,0.97,0.90), mix(vec3(1.0,0.88,0.84), vec3(1.0,0.68,0.60), w), vec3(0.70,0.78,0.98));\n  float gN = nightW();\n  c += glowC*(exp(-rg*13.0)*mix(0.34,0.20,gN) + exp(-rg*34.0)*mix(0.55,0.42,gN));\n  c += pal(vec3(1.0,1.0,0.96), vec3(1.0,0.97,0.94), vec3(0.93,0.96,1.0))*smoothstep(0.016,0.004,r)*mix(0.8,0.68,gN);\n  float lo = fbm3(vec2(uv.x*3.4 + T*0.055, (uv.y-HZ)*16.0));\n  float cum = smoothstep(0.50,0.78, lo)*exp(-abs(uv.y-(HZ+0.055))*26.0);\n  c = mix(c, mix(vec3(0.40,0.26,0.44), vec3(0.82,0.46,0.60), smoothstep(0.0,0.6,lo)), clamp(cum*1.1,0.0,0.9));\n  return c;\n}\n\nfloat waveH(vec2 wIn, float att){\n  vec2 w = wIn*3.2;\n  float h = 0.0;\n  float s1 = sin(w.y*1.7 + sin(w.x*0.5 + T*0.11)*0.9 + T*0.38);\n  h += (s1 - 0.30*s1*abs(s1))*0.075;\n  float s2 = sin(w.y*3.1 - w.x*0.8 + T*0.60);\n  h += (s2 - 0.25*s2*abs(s2))*0.042;\n  h += sin(w.x*2.2 + w.y*1.1 - T*0.27)*0.024;\n  vec2 dw = w*vec2(1.0,1.6) + vec2(0.0, T*0.22);\n  h += (fbm(dw + fbm3(dw*0.5)*0.6)-0.5)*0.26;\n  h += (noise(w*vec2(3.2,5.5) + vec2(T*0.12, T*0.55))-0.5)*0.090*att;\n  h += (noise(w*vec2(7.5,12.0) + vec2(T*0.30, T*1.05))-0.5)*0.045*att*att;\n  return h*WAVES;\n}\n\nvoid main(){\n  vec2 uv = gl_FragCoord.xy / R.xy;\n  vec3 col;\n  if(uv.y > HZ){\n    col = skyCol(uv);\n  } else {\n    float dy = HZ - uv.y;\n    float z = min(0.085/(dy + 0.0060), 18.0);\n    float att = smoothstep(0.0, 0.22, dy) * smoothstep(18.0, 6.0, z);\n    vec2 w = vec2((uv.x-0.5)*z*1.9, z);\n    float e = 0.012 + z*0.014;\n    float h  = waveH(w, att);\n    float hx = waveH(w + vec2(e,0.0), att);\n    float hy = waveH(w + vec2(0.0,e), att);\n    vec2 n = vec2(h-hx, h-hy) / e;\n    n *= att*0.055;\n\n    vec2 ruv = vec2(uv.x + n.x*0.34, HZ + dy*0.92 + n.y*0.32);\n    ruv.y = max(ruv.y, HZ + 0.001);\n    vec3 refl = skyLite(ruv);\n\n    vec3 deep = mix(pal(vec3(0.035,0.135,0.300), vec3(0.07,0.08,0.22), vec3(0.045,0.070,0.185)), pal(vec3(0.100,0.270,0.470), vec3(0.16,0.15,0.34), vec3(0.110,0.155,0.320)), att);\n    float fres = mix(0.95, 0.20, clamp(dy*2.8,0.0,1.0));\n    col = mix(deep, refl, fres);\n\n    float colM = exp(-abs((uv.x - sunX()) + n.x*0.9)*6.0);\n    float sparkle = pow(clamp(1.0 - abs(n.y*6.0 + 0.12), 0.0, 1.0), 5.0);\n    col += pal(vec3(1.0,0.99,0.94), vec3(1.0,0.88,0.86), vec3(0.80,0.87,1.0))*sparkle*colM*mix(0.95,1.05,nightW())*mix(1.0,1.35,dayW())*att;\n    float glit = pow(clamp(fbm3(w*vec2(5.0,9.0)+vec2(T*0.22,T*0.85)) + n.y*1.2, 0.0, 1.0), 8.0);\n    col += pal(vec3(1.0,1.0,0.95), vec3(1.0,0.93,0.91), vec3(0.86,0.91,1.0))*glit*colM*mix(1.7,1.9,nightW())*mix(1.0,1.45,dayW())*att*att;\n    col += pal(vec3(0.85,0.93,1.0), vec3(1.0,0.72,0.74), vec3(0.55,0.64,0.92))*colM*0.10*(1.0-att);\n    col = mix(col, skyLite(vec2(uv.x, HZ+0.004)), exp(-dy*120.0)*0.75);\n  }\n  vec2 q = uv-0.5;\n  col *= 1.0 - dot(q,q)*0.35;\n  col += (hash(uv*R.xy + fract(T))-0.5)*0.018;\n  gl_FragColor = vec4(pow(clamp(col,0.0,1.0), vec3(0.94)), 1.0);\n}'
const VERTEX_SHADER = 'attribute vec2 p; void main(){ gl_Position = vec4(p,0.,1.); }'

/** 갈매기·부표·배 — 셰이더로 그리기엔 과한 요소는 DOM으로 */
const OVERLAY_KEYFRAMES =
  '@keyframes obShip{from{transform:translateX(-6%)}to{transform:translateX(106%)}}\n@keyframes obBob{0%,100%{transform:translateY(0) rotate(-.7deg)}50%{transform:translateY(1.6px) rotate(.7deg)}}\n@keyframes obGlide{from{transform:translateX(-8%)}to{transform:translateX(108%)}}\n@keyframes obLamp{0%,100%{opacity:.55}42%{opacity:1}68%{opacity:.7}}\n@keyframes obBeacon{0%,88%{opacity:0}92%{opacity:1}100%{opacity:0}}\n@keyframes obSoar{0%,100%{transform:translateY(0)}35%{transform:translateY(-14px)}70%{transform:translateY(6px)}}\n@keyframes obFlapL{0%,100%{transform:rotate(-16deg)}50%{transform:rotate(-38deg)}}\n@keyframes obFlapR{0%,100%{transform:rotate(16deg)}50%{transform:rotate(38deg)}}\n@keyframes obBuoy{0%,100%{transform:translateY(0) rotate(-4deg)}50%{transform:translateY(4px) rotate(4deg)}}\n'
const OVERLAY = {
  gulls:
    '<div style="position:absolute;inset:0;pointer-events:none">\n      <div style="position:absolute;left:0;top:22%;width:100%;animation:obGlide 68s linear infinite">\n        <div style="animation:obSoar 9s ease-in-out infinite">\n          <div style="position:relative;width:26px;height:10px">\n            <div style="position:absolute;left:0;top:4px;width:13px;height:2px;border-radius:2px;background:rgba(28,20,44,.5);transform-origin:right center;animation:obFlapL 2.6s ease-in-out infinite"></div>\n            <div style="position:absolute;left:13px;top:4px;width:13px;height:2px;border-radius:2px;background:rgba(28,20,44,.5);transform-origin:left center;animation:obFlapR 2.6s ease-in-out infinite"></div>\n          </div>\n        </div>\n      </div>\n      <div style="position:absolute;left:0;top:29%;width:100%;animation:obGlide 88s linear 6s infinite">\n        <div style="animation:obSoar 11s ease-in-out .8s infinite">\n          <div style="position:relative;width:19px;height:8px">\n            <div style="position:absolute;left:0;top:3px;width:9.5px;height:1.6px;border-radius:2px;background:rgba(28,20,44,.42);transform-origin:right center;animation:obFlapL 3.1s ease-in-out .3s infinite"></div>\n            <div style="position:absolute;left:9.5px;top:3px;width:9.5px;height:1.6px;border-radius:2px;background:rgba(28,20,44,.42);transform-origin:left center;animation:obFlapR 3.1s ease-in-out .3s infinite"></div>\n          </div>\n        </div>\n      </div>\n      <div style="position:absolute;left:0;top:18%;width:100%;animation:obGlide 104s linear 22s infinite">\n        <div style="animation:obSoar 13s ease-in-out 1.6s infinite">\n          <div style="position:relative;width:14px;height:6px">\n            <div style="position:absolute;left:0;top:2px;width:7px;height:1.4px;border-radius:2px;background:rgba(28,20,44,.34);transform-origin:right center;animation:obFlapL 3.6s ease-in-out .7s infinite"></div>\n            <div style="position:absolute;left:7px;top:2px;width:7px;height:1.4px;border-radius:2px;background:rgba(28,20,44,.34);transform-origin:left center;animation:obFlapR 3.6s ease-in-out .7s infinite"></div>\n          </div>\n        </div>\n      </div>\n    </div>',
  buoy: '<div style="position:absolute;left:71%;top:63%;pointer-events:none;animation:obBuoy 4.6s ease-in-out infinite">\n      <div style="position:relative">\n        <div style="width:11px;height:15px;border-radius:50% 50% 42% 42%;background:rgba(178,122,138,.62);box-shadow:inset 0 -4px 0 rgba(255,255,255,.26)"></div>\n        <div style="position:absolute;left:5px;top:-7px;width:1.5px;height:8px;background:rgba(28,20,44,.42)"></div>\n        <div style="position:absolute;left:-5px;top:14px;width:21px;height:5px;border-radius:50%;background:rgba(255,255,255,.16);filter:blur(2.5px)"></div>\n      </div>\n    </div>',
  ship: '<div style="position:absolute;left:0;top:52.5%;width:100%;height:0;pointer-events:none">\n\n      <div style="position:absolute;left:0;bottom:-2px;width:100%;animation:obShip 110s linear -12s infinite">\n        <div style="position:relative;display:inline-block;animation:obBob 7.5s ease-in-out infinite">\n          <div style="width:19px;height:5px;background:rgba(10,8,26,.62);clip-path:polygon(8% 0,92% 0,80% 100%,20% 100%)"></div>\n          <div style="position:absolute;left:8px;bottom:5px;width:1.5px;height:8px;background:rgba(10,8,26,.55)"></div>\n          <div style="position:absolute;left:4px;bottom:1.5px;width:2px;height:2px;border-radius:1px;background:rgba(255,228,168,.95);animation:obLamp 5.5s ease-in-out infinite"></div>\n          <div style="position:absolute;left:8px;bottom:1.5px;width:2px;height:2px;border-radius:1px;background:rgba(255,228,168,.8);animation:obLamp 5.5s ease-in-out .8s infinite"></div>\n          <div style="position:absolute;left:12px;bottom:1.5px;width:2px;height:2px;border-radius:1px;background:rgba(255,228,168,.9);animation:obLamp 5.5s ease-in-out 1.7s infinite"></div>\n          <div style="position:absolute;left:2px;bottom:-6px;width:15px;height:8px;background:rgba(255,224,160,.30);filter:blur(3.5px);animation:obLamp 5.5s ease-in-out infinite"></div>\n        </div>\n      </div>\n\n      <div style="position:absolute;left:0;bottom:-1px;width:100%;animation:obShip 150s linear -86s infinite">\n        <div style="position:relative;display:inline-block;animation:obBob 9s ease-in-out .6s infinite">\n          <div style="width:12px;height:3.5px;background:rgba(10,8,26,.5);clip-path:polygon(10% 0,90% 0,78% 100%,22% 100%)"></div>\n          <div style="position:absolute;left:5px;bottom:3.5px;width:1.2px;height:6px;background:rgba(10,8,26,.44)"></div>\n          <div style="position:absolute;left:4.5px;bottom:9px;width:2.5px;height:2.5px;border-radius:50%;background:rgba(214,232,255,.9);animation:obBeacon 7s linear infinite"></div>\n        </div>\n      </div>\n\n      <div style="position:absolute;left:0;bottom:-3px;width:100%;animation:obShip 92s linear -58s infinite">\n        <div style="position:relative;display:inline-block;animation:obBob 6.4s ease-in-out 1.4s infinite">\n          <div style="width:26px;height:6px;background:rgba(10,8,26,.66);clip-path:polygon(6% 0,94% 0,83% 100%,17% 100%)"></div>\n          <div style="position:absolute;left:6px;bottom:6px;width:13px;height:4px;background:rgba(10,8,26,.6)"></div>\n          <div style="position:absolute;left:7px;bottom:7px;width:2px;height:2px;border-radius:1px;background:rgba(255,226,164,.95);animation:obLamp 4.4s ease-in-out infinite"></div>\n          <div style="position:absolute;left:11px;bottom:7px;width:2px;height:2px;border-radius:1px;background:rgba(255,226,164,.75);animation:obLamp 4.4s ease-in-out 1.1s infinite"></div>\n          <div style="position:absolute;left:15px;bottom:7px;width:2px;height:2px;border-radius:1px;background:rgba(255,226,164,.9);animation:obLamp 4.4s ease-in-out 2.2s infinite"></div>\n          <div style="position:absolute;left:3px;bottom:2px;width:2px;height:2px;border-radius:1px;background:rgba(255,226,164,.85);animation:obLamp 4.4s ease-in-out .5s infinite"></div>\n          <div style="position:absolute;left:20px;bottom:2px;width:2px;height:2px;border-radius:1px;background:rgba(255,226,164,.7);animation:obLamp 4.4s ease-in-out 1.6s infinite"></div>\n          <div style="position:absolute;left:2px;bottom:-8px;width:23px;height:11px;background:rgba(255,222,156,.34);filter:blur(4.5px);animation:obLamp 4.4s ease-in-out infinite"></div>\n        </div>\n      </div>\n\n      <div style="position:absolute;left:0;bottom:0;width:100%;animation:obShip 190s linear -152s infinite">\n        <div style="position:relative;display:inline-block;animation:obBob 11s ease-in-out 2.2s infinite">\n          <div style="width:8px;height:2.5px;background:rgba(10,8,26,.42);clip-path:polygon(12% 0,88% 0,76% 100%,24% 100%)"></div>\n          <div style="position:absolute;left:3.4px;bottom:2.5px;width:1px;height:4px;background:rgba(10,8,26,.36)"></div>\n        </div>\n      </div>\n\n    </div>',
}

const RENDER_SCALE = 0.62 // 내부 렌더 해상도 배수 — 화질과 부하의 균형점
const TARGET_FPS = 40

/** 실제 시각을 0(낮) ~ 2(밤) 연속값으로. 경계는 부드럽게 넘어갑니다. */
function autoTone(): number {
  const d = new Date()
  const h = d.getHours() + d.getMinutes() / 60
  if (h >= 9 && h < 16) return 0
  if (h >= 16 && h < 17.5) return (h - 16) / 1.5
  if (h >= 17.5 && h < 19) return 1
  if (h >= 19 && h < 20.5) return 1 + (h - 19) / 1.5
  if (h >= 20.5 || h < 4.5) return 2
  if (h >= 4.5 && h < 6) return 2 - (h - 4.5) / 1.5
  if (h >= 6 && h < 7.5) return 1
  return 1 - (h - 7.5) / 1.5
}

/** 실제 시각에 맞춘 해·달의 하늘 횡단 진행률 */
function autoArc(): number {
  const d = new Date()
  const h = d.getHours() + d.getMinutes() / 60
  return h >= 6 && h < 19 ? (h - 6) / 13 : (h >= 19 ? h - 19 : h + 5) / 11
}

/**
 * 사실적인 바다·하늘 배경.
 *
 *   <ocean-bg sun-height="0.45" dim="0.4" blur-px="5"></ocean-bg>
 *
 * 부모를 가득 채우므로 부모에 position 이 있어야 한다.
 * theme 을 주지 않으면 OceanTheme 전역 상태를 따르고 전환은 보간된다.
 */
export class OceanBg extends BaseElement {
  static observedAttributes = [
    'theme',
    'wave-intensity',
    'sun-height',
    'motion-speed',
    'day-cycle-min',
    'dim',
    'blur-px',
  ]

  private canvas!: HTMLCanvasElement
  private fx!: HTMLDivElement
  private fxHtml = ''
  private raf = 0
  private ro?: ResizeObserver
  private tick = 0
  private tone: number | null = null
  private resume?: () => void
  private clock = 0
  private onThemeChange = () => this.syncOverlay()
  // initGL() 은 캔버스가 다시 붙을 때마다 다시 돈다. 이 리스너를 그 안에 두면
  // 오래된 clock 을 붙들고 있는 클로저가 쌓여 파도 위상 이어받기가 어긋난다.
  private onPageHide = () => {
    try {
      sessionStorage.setItem('oceanClock', String(this.clock))
    } catch {
      /* 무시 */
    }
  }

  /** 지금 적용 중인 테마 ('auto'가 해석된 값) */
  get effectiveTheme(): EffectiveTheme {
    const t = this.theme()
    if (t !== 'auto') return t
    const v = autoTone()
    return v < 0.6 ? 'day' : v > 1.4 ? 'night' : 'sunset'
  }

  protected build(): void {
    const root = this.attachShadow({ mode: 'open' })
    root.innerHTML =
      '<style>' +
      ':host{position:absolute;inset:0;display:block;overflow:hidden;background:var(--color-abyss)}' +
      'canvas{position:absolute;inset:0;width:100%;height:100%;display:block}' +
      '.fx{position:absolute;inset:0;pointer-events:none}' +
      OVERLAY_KEYFRAMES +
      '</style>' +
      '<canvas part="canvas"></canvas>' +
      '<div class="fx"></div>'

    this.canvas = root.querySelector('canvas') as HTMLCanvasElement
    this.fx = root.querySelector('.fx') as HTMLDivElement

    // 'auto'일 때 시간이 흐르면 색도 따라가야 한다
    this.tick = window.setInterval(() => {
      if (this.theme() === 'auto') this.syncOverlay()
    }, 60000)

    this.syncOverlay()
    this.initGL()
  }

  override connectedCallback(): void {
    super.connectedCallback()
    // build() 는 1회만 돌아서 재마운트 후 다시 붙지 않는다. 같은 함수 참조라 중복
    // 등록되지도 않는다.
    window.addEventListener('oceantheme', this.onThemeChange)
    window.addEventListener('pagehide', this.onPageHide)
    this.resume?.()
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback()
    window.removeEventListener('oceantheme', this.onThemeChange)
    window.removeEventListener('pagehide', this.onPageHide)
    window.clearInterval(this.tick)
    cancelAnimationFrame(this.raf)
    this.ro?.disconnect()
  }

  protected override update(): void {
    this.applyFilter()
    this.syncOverlay()
  }

  private theme(): Theme {
    const attr = this.getAttribute('theme') as Theme | null
    if (attr) return attr
    const store = (window as unknown as { OceanTheme?: { get(): Theme } }).OceanTheme
    if (store) return store.get()
    const win = window as unknown as { __oceanTheme?: Theme }
    if (win.__oceanTheme) return win.__oceanTheme
    let t: string | null = null
    try {
      t = localStorage.getItem('oceanTheme')
    } catch {
      /* 무시 */
    }
    return (t as Theme) || 'sunset'
  }

  /** 낮이면 갈매기·부표, 밤이면 조명 켜진 배. 노을에는 두지 않습니다. */
  private syncOverlay(): void {
    if (!this.fx) return
    const th = this.effectiveTheme
    const html = th === 'night' ? OVERLAY.ship : th === 'day' ? OVERLAY.gulls + OVERLAY.buoy : ''
    if (this.fxHtml === html) return
    this.fxHtml = html
    this.fx.innerHTML = html
  }

  private applyFilter(): void {
    if (!this.canvas) return
    const dim = this.num('dim', 0)
    const blur = this.num('blur-px', 0)
    this.canvas.style.filter =
      'brightness(' + (1 - dim).toFixed(3) + ')' + (blur ? ' blur(' + blur + 'px)' : '')
    this.canvas.style.transform = blur ? 'scale(1.04)' : 'none'
  }

  private initGL(): void {
    const cv = this.canvas
    // 컨텍스트는 캔버스당 하나만 — 재초기화가 컨텍스트를 소진시키지 않도록 캐시
    const cached = cv as HTMLCanvasElement & { __gl?: WebGLRenderingContext | null }
    const gl =
      cached.__gl ??
      (cached.__gl = cv.getContext('webgl', {
        antialias: false,
        alpha: false,
        preserveDrawingBuffer: true,
      }))
    if (!gl) {
      console.warn('[ocean-bg] WebGL을 사용할 수 없습니다')
      return
    }
    this.applyFilter()

    const compile = (type: number, source: string): WebGLShader => {
      const sh = gl.createShader(type) as WebGLShader
      gl.shaderSource(sh, source)
      gl.compileShader(sh)
      if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS))
        console.error('[ocean-bg]', gl.getShaderInfoLog(sh))
      return sh
    }

    const prog = gl.createProgram() as WebGLProgram
    gl.attachShader(prog, compile(gl.VERTEX_SHADER, VERTEX_SHADER))
    gl.attachShader(prog, compile(gl.FRAGMENT_SHADER, FRAGMENT_SHADER))
    gl.linkProgram(prog)
    gl.useProgram(prog)

    const buf = gl.createBuffer()
    gl.bindBuffer(gl.ARRAY_BUFFER, buf)
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW)
    const loc = gl.getAttribLocation(prog, 'p')
    gl.enableVertexAttribArray(loc)
    gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0)

    const uR = gl.getUniformLocation(prog, 'R')
    const uT = gl.getUniformLocation(prog, 'T')
    const uW = gl.getUniformLocation(prog, 'WAVES')
    const uS = gl.getUniformLocation(prog, 'SUNH')
    const uD = gl.getUniformLocation(prog, 'DAY')
    const uTH = gl.getUniformLocation(prog, 'TH')

    /** 크기가 바뀐 직후 한 장 다시 그린다. `draw` 가 아래에서 정의돼 변수로 받아 둔다. */
    let redraw: (() => void) | null = null

    const resize = (): void => {
      const w = Math.max(2, Math.round((cv.clientWidth || 1440) * RENDER_SCALE))
      const h = Math.max(2, Math.round((cv.clientHeight || 1024) * RENDER_SCALE))
      if (w !== cv.width || h !== cv.height) {
        // width·height 를 넣는 순간 캔버스가 비워진다. 루프는 40fps 라 다음 그림까지
        // 최대 25ms 가 비고, 그동안 뒤의 어두운 바탕이 비쳐 검게 깜빡인다.
        cv.width = w
        cv.height = h
        gl.viewport(0, 0, w, h)
        gl.uniform2f(uR, w, h)
        redraw?.()
      }
    }
    resize()
    this.ro?.disconnect()
    this.ro = new ResizeObserver(resize)
    this.ro.observe(cv)

    const toneTarget = (): number => {
      const t = this.theme()
      if (t === 'auto') return autoTone()
      return t === 'day' ? 0 : t === 'night' ? 2 : 1
    }
    const arcPhase = (clock: number): number => {
      if (this.theme() === 'auto') return autoArc()
      const cycle = this.num('day-cycle-min', 6) * 60
      if (cycle <= 0) return 0.5 - (1 - this.num('sun-height', 0.45)) * 0.34
      return (((clock / cycle) % 1) + 1) % 1
    }

    // 화면을 넘나들어도 파도 위상이 이어지도록 시계를 공유·보존
    const win = window as unknown as { __oceanClock?: number }
    let clock = win.__oceanClock
    if (clock == null) {
      try {
        clock = parseFloat(sessionStorage.getItem('oceanClock') || '0')
      } catch {
        clock = 0
      }
    }
    this.clock = clock

    if (this.tone == null) this.tone = toneTarget()

    const FRAME = 1000 / TARGET_FPS
    let last = performance.now()
    let acc = 0

    const draw = (): void => {
      gl.uniform1f(uT, clock as number)
      gl.uniform1f(uW, this.num('wave-intensity', 1))
      gl.uniform1f(uS, this.num('sun-height', 0.45))
      gl.uniform1f(uD, arcPhase(clock as number))
      gl.uniform1f(uTH, this.tone as number)
      gl.drawArrays(gl.TRIANGLES, 0, 3)
    }

    const loop = (now: number): void => {
      if (!cv.isConnected) return
      this.raf = requestAnimationFrame(loop)
      const dt = Math.min(now - last, 100)
      last = now
      acc += dt
      if (acc < FRAME) return // 40fps로 제한 — 배경에 프레임을 낭비하지 않는다
      clock = (clock as number) + acc * 0.001 * this.num('motion-speed', 1)
      acc = 0
      win.__oceanClock = clock
      this.clock = clock as number
      // 테마 전환은 즉시 바꾸지 않고 보간해 자연스럽게
      const target = toneTarget()
      this.tone =
        (this.tone as number) + (target - (this.tone as number)) * Math.min(1, dt * 0.0035)
      if (Math.abs(target - (this.tone as number)) < 0.002) this.tone = target
      draw()
    }

    win.__oceanClock = clock
    this.tone = toneTarget()
    redraw = draw // 이제부터 크기가 바뀌면 그 자리에서 다시 그린다
    draw() // 첫 프레임 즉시 — rAF가 멈춘 탭에서도 검은 화면이 남지 않게
    this.raf = requestAnimationFrame(loop)
    this.resume = () => {
      cancelAnimationFrame(this.raf)
      last = performance.now()
      this.raf = requestAnimationFrame(loop)
    }
  }
}

define('ocean-bg', OceanBg)
