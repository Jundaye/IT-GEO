/* 로그인 (2026-11-27) : 시안(reference/design_ITGEO/IT-GEO_인트로_로그인_시안 (1).html)의 스크립트 그대로.
   바꾼 것 : DEMO=false(실제 login.do 전송), 사원번호 저장 키 = 예전과 같은 itgeo.loginId */
(()=>{
const cv=document.getElementById('cv'),ctx=cv.getContext('2d'),scene=document.getElementById('scene');
const timeEl=document.getElementById('time'),clock=document.getElementById('clock'),headline=document.getElementById('headline');
const reduce=matchMedia('(prefers-reduced-motion: reduce)').matches;
let W=0,H=0,DPR=1,ground=0,bld=[],far=[],targets=[],clouds=[],drops=[];
let hour=(()=>{const d=new Date();return d.getHours()+d.getMinutes()/60})();
let weather='clear',playing=false,introT=reduce?99:0,lens={x:0,y:0,r:84,tx:0,ty:0},lastMove=-9,wanderI=0,wanderT=0,finish=null;

function rng(s){return()=>{s|=0;s=s+0x6D2B79F5|0;let t=Math.imul(s^s>>>15,1|s);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
const TAGS=[['반지하 · 침수 이력',91],['옥탑 · 사용승인 1987',88],['고시원 · 1인 가구',86],['반지하 · 노후 36년',84],['지하 1층 · 저지대',82],['옥탑 · 위반건축물',81],['다가구 · 임대료 체납',80]];
const ROWS=['신월동 ○○-○  지하 1층','신정동 ○○○-○  옥탑','구로동 ○○○  3층 고시원','개봉동 ○○-○  반지하','대림동 ○○○-○  지하','신길동 ○○-○○  옥탑','문래동 ○○  2층'];

function build(){
  const r=rng(7);ground=Math.round(H*.78);bld=[];far=[];targets=[];
  for(let x=-20;x<W+40;){const w=70+r()*90,h=H*.22+r()*H*.28;far.push({x,w,h});x+=w+6}
  let x=-10;
  while(x<W+20){
    const w=Math.round(64+r()*70),fl=2+Math.floor(r()*4),fh=Math.max(26,Math.min(38,H*.05)),h=fl*fh+10;
    const b={x,w,h,fl,fh,base:r()<.45,roof:r()<.35,tone:r(),win:[]};
    const cols=Math.max(2,Math.floor((w-12)/22));
    for(let f=0;f<fl;f++)for(let c=0;c<cols;c++){
      const ww=10,wx=x+8+c*((w-16-ww)/Math.max(1,cols-1)),wy=ground-h+10+f*fh+6;
      b.win.push({x:wx,y:wy,w:ww,h:fh*.42,lit:r()<.24,ph:r()*6})}
    if(b.base)for(let c=0;c<Math.max(1,cols-1);c++)b.win.push({x:x+12+c*22,y:ground-7,w:12,h:7,base:true,lit:r()<.3,ph:r()*6});
    if(b.roof){const rw=Math.min(34,w*.45);b.rbox={x:x+w-rw-6,y:ground-h-20,w:rw,h:20};b.win.push({x:b.rbox.x+6,y:b.rbox.y+6,w:9,h:8,lit:r()<.5,ph:r()*6,roof:true})}
    bld.push(b);x+=w+Math.round(4+r()*10);
  }
  // 분석 대상 가구 선정: 반지하·옥탑 우선
  const cand=[];bld.forEach(b=>b.win.forEach(w=>{if(w.x>30&&w.x<W-40)cand.push(w)}));
  const pri=cand.filter(w=>w.base||w.roof),rest=cand.filter(w=>!w.base&&!w.roof);
  const pick=[];const step=Math.max(1,Math.floor(pri.length/5));
  for(let i=0;i<pri.length&&pick.length<5;i+=step)pick.push(pri[i]);
  for(let i=Math.floor(rest.length*.3);pick.length<7&&i<rest.length;i+=Math.floor(rest.length*.27)+1)pick.push(rest[i]);
  pick.forEach((w,i)=>{w.flag=true;w.tag=TAGS[i%TAGS.length];targets.push(w)});
  clouds=Array.from({length:6},(_,i)=>({x:r()*W,y:H*(.08+r()*.25),s:.6+r()*.9,v:4+r()*6}));
  drops=Array.from({length:220},()=>({x:r()*W,y:r()*H,v:.6+r()*.6}));
  lens.x=lens.tx=W*.5;lens.y=lens.ty=ground-60;
}
function resize(){const rc=scene.getBoundingClientRect();DPR=Math.min(2,devicePixelRatio||1);W=rc.width;H=rc.height;cv.width=W*DPR;cv.height=H*DPR;ctx.setTransform(DPR,0,0,DPR,0,0);build()}
new ResizeObserver(resize).observe(scene);

// 하늘 색 키프레임 [시각, 위, 아래, 어둠]
const K=[[0,'#070d1f','#16244a',1],[5,'#0a1430','#1f2f5a',1],[6.2,'#3b4d86','#f0a37a',.55],[7.5,'#7fb2e6','#f5d9b8',.12],[12,'#5f9fe0','#d6e8f8',0],[16.3,'#6ea6e0','#e9e2cf',.05],[17.6,'#4d5f9e','#f39a62',.35],[18.6,'#26306a','#b95d6a',.7],[19.6,'#0f1838','#2a2f5c',.95],[24,'#070d1f','#16244a',1]];
const hx=h=>[parseInt(h.slice(1,3),16),parseInt(h.slice(3,5),16),parseInt(h.slice(5,7),16)];
const mix=(a,b,t)=>a.map((v,i)=>Math.round(v+(b[i]-v)*t));
const rgb=(c,a=1)=>`rgba(${c[0]},${c[1]},${c[2]},${a})`;
function sky(h){for(let i=0;i<K.length-1;i++){const a=K[i],b=K[i+1];if(h>=a[0]&&h<=b[0]){const t=(h-a[0])/(b[0]-a[0]);return{top:mix(hx(a[1]),hx(b[1]),t),bot:mix(hx(a[2]),hx(b[2]),t),dark:a[3]+(b[3]-a[3])*t}}}return{top:hx(K[0][1]),bot:hx(K[0][2]),dark:1}}

function fmt(h){const hh=Math.floor(h)%24,mm=Math.floor((h-Math.floor(h))*60);return{t:`${String(hh%12||12).padStart(2,' ')}:${String(mm).padStart(2,'0')}`,ap:hh<12?'오전':'오후'}}

function drawCity(s,blue){
  const d=s.dark,wet=weather==='rain'?.15:0,over=weather==='cloud'||weather==='rain'?.25:0;
  // 원경 아파트
  const farC=blue?[22,52,98]:mix(mix([150,170,200],[20,28,52],d),[120,128,140],over*.5);
  if(blue){ctx.strokeStyle='rgba(79,147,230,.35)';far.forEach(f=>ctx.strokeRect(f.x+.5,ground-f.h+.5,f.w,f.h))}else{ctx.fillStyle=rgb(farC,.9);far.forEach(f=>ctx.fillRect(f.x,ground-f.h,f.w,f.h))}
  // 지면
  ctx.fillStyle=blue?'#0b1d3c':rgb(mix([118,124,132],[18,22,34],d));ctx.fillRect(0,ground,W,H-ground);
  ctx.fillStyle=blue?'#14305e':rgb(mix([150,154,160],[30,34,48],d),1);ctx.fillRect(0,ground,W,3);
  bld.forEach(b=>{
    const c=blue?[16,40,80]:mix(mix([212,204,192],[226,222,214],b.tone),[34,40,60],d*.92);
    ctx.fillStyle=rgb(c);ctx.fillRect(b.x,ground-b.h,b.w,b.h);
    if(b.rbox){ctx.fillRect(b.rbox.x,b.rbox.y,b.rbox.w,b.rbox.h)}
    if(blue){ctx.strokeStyle='#4f93e6';ctx.lineWidth=1;ctx.strokeRect(b.x+.5,ground-b.h+.5,b.w-1,b.h-1);if(b.rbox)ctx.strokeRect(b.rbox.x+.5,b.rbox.y+.5,b.rbox.w-1,b.rbox.h-1)}
    else{ctx.fillStyle=rgb(mix(c,[0,0,0],.18));ctx.fillRect(b.x,ground-b.h,b.w,4)}
    b.win.forEach(w=>{
      if(blue){ctx.strokeStyle=w.flag?'#ff9a4a':'#2e5f9f';ctx.strokeRect(w.x+.5,w.y+.5,w.w,w.h);if(w.flag){ctx.fillStyle='#ff8a3d';ctx.fillRect(w.x,w.y,w.w+1,w.h+1)}return}
      let col=mix([120,140,165],[40,46,66],d);let a=1;
      if(d>.4&&w.lit&&!w.flag){col=[205,215,235];a=.12+.25*(d-.4)}
      ctx.fillStyle=rgb(col,a);ctx.fillRect(w.x,w.y,w.w,w.h);
    });
  });
  if(!blue&&wet){ctx.fillStyle=`rgba(180,200,230,${wet*(1-d*.5)})`;ctx.fillRect(0,ground+3,W,H-ground)}
}
function drawFlagsNight(s,now){
  const d=s.dark;if(d<.35)return;const k=(d-.35)/.65;
  targets.forEach((w,i)=>{
    const p=.85+.15*Math.sin(now/600+w.ph),cx=w.x+w.w/2,cy=w.y+w.h/2;
    const g=ctx.createRadialGradient(cx,cy,0,cx,cy,30);g.addColorStop(0,`rgba(255,170,60,${.8*k*p})`);g.addColorStop(1,'rgba(255,190,90,0)');
    ctx.fillStyle=g;ctx.fillRect(cx-30,cy-30,60,60);
    ctx.fillStyle=`rgba(255,200,110,${k})`;ctx.fillRect(w.x,w.y,w.w,w.h);
  });
}
function drawWeather(s,dt){
  const d=s.dark;
  clouds.forEach(c=>{c.x+=c.v*dt*(weather==='clear'?1:1.6);if(c.x>W+120)c.x=-160;
    const a=weather==='clear'?.35:.75,col=mix([255,255,255],[70,80,110],d);
    ctx.fillStyle=rgb(weather==='clear'?col:mix(col,[120,128,140],.45),a*(1-d*.5));
    const s2=c.s*(weather==='clear'?1:1.5);
    [[0,0,40],[34,-10,32],[64,2,36],[30,10,38]].forEach(([dx,dy,r])=>{ctx.beginPath();ctx.ellipse(c.x+dx*s2,c.y+dy*s2,r*s2*1.3,r*s2*.7,0,0,7);ctx.fill()})});
  if(weather==='rain'){ctx.strokeStyle=`rgba(200,215,240,${.45})`;ctx.lineWidth=1;ctx.beginPath();
    drops.forEach(p=>{p.y+=p.v*900*dt;p.x-=p.v*120*dt;if(p.y>H){p.y=-10;p.x=Math.random()*W*1.1}ctx.moveTo(p.x,p.y);ctx.lineTo(p.x-3,p.y+12)});ctx.stroke()}
  if(weather==='snow'){ctx.fillStyle='rgba(255,255,255,.85)';
    drops.forEach((p,i)=>{p.y+=p.v*60*dt;p.x+=Math.sin(p.y/30+i)*.3;if(p.y>H){p.y=-5;p.x=Math.random()*W}ctx.beginPath();ctx.arc(p.x,p.y,1.2+p.v,0,7);ctx.fill()})}
}
function drawLens(now){
  const {x,y,r}=lens;
  ctx.save();ctx.beginPath();ctx.arc(x,y,r,0,7);ctx.clip();
  ctx.fillStyle='#081733';ctx.fillRect(x-r,y-r,r*2,r*2);
  ctx.strokeStyle='rgba(80,140,220,.18)';ctx.lineWidth=1;ctx.beginPath();
  for(let gx=Math.floor((x-r)/24)*24;gx<x+r;gx+=24){ctx.moveTo(gx+.5,y-r);ctx.lineTo(gx+.5,y+r)}
  for(let gy=Math.floor((y-r)/24)*24;gy<y+r;gy+=24){ctx.moveTo(x-r,gy+.5);ctx.lineTo(x+r,gy+.5)}ctx.stroke();
  drawCity(null||{dark:0},true);
  ctx.restore();
  // 테두리와 손잡이
  ctx.strokeStyle='rgba(255,255,255,.95)';ctx.lineWidth=3;ctx.beginPath();ctx.arc(x,y,r,0,7);ctx.stroke();
  ctx.lineWidth=7;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(x+r*.72,y+r*.72);ctx.lineTo(x+r*1.15,y+r*1.15);ctx.stroke();
  // 렌즈 안 대상 가구 라벨
  if(finish)return;
  ctx.font='600 12px "Noto Sans KR",sans-serif';
  targets.forEach(w=>{const cx=w.x+w.w/2,cy=w.y+w.h/2;if(Math.hypot(cx-x,cy-y)>r-8)return;
    const label=`위험 ${w.tag[1]}점 · ${w.tag[0]}`,tw=ctx.measureText(label).width+14;
    let lx=Math.min(Math.max(cx-tw/2,6),W-tw-6),ly=cy-r*.5<y-r?y-r-30:cy-34;ly=Math.max(6,ly);
    ctx.fillStyle='rgba(8,23,51,.92)';ctx.strokeStyle='#ff9a4a';ctx.lineWidth=1;
    ctx.beginPath();ctx.roundRect(lx,ly,tw,22,4);ctx.fill();ctx.stroke();
    ctx.fillStyle='#fff';ctx.fillText(label,lx+7,ly+15);
    ctx.strokeStyle='rgba(255,154,74,.8)';ctx.beginPath();ctx.moveTo(cx,cy);ctx.lineTo(cx,ly+22);ctx.stroke();
  });
}
function drawIntro(t){
  // 0~1.2s 목록 등장, 1.6s~ 각 줄이 점이 되어 해당 가구로 이동, ~4.2s 종료
  if(t>5.4)return;
  const cw=Math.min(330,W*.62),cx=Math.max(24,W*.06),cy=H*.2,rh=26,ch=50+ROWS.length*rh;
  const cardA=Math.min(1,t/.4)*(t>3.5?Math.max(0,1-(t-3.5)/.6):1);
  ctx.globalAlpha=cardA;ctx.fillStyle='#fbfaf6';ctx.strokeStyle='#d5d0c4';
  ctx.beginPath();ctx.roundRect(cx,cy,cw,ch,4);ctx.fill();ctx.stroke();
  ctx.fillStyle='#55504a';ctx.font='600 12px "Noto Sans KR",sans-serif';ctx.fillText('현장조사 대상 목록  (총 7건)',cx+14,cy+24);
  ctx.strokeStyle='#e4dfd4';ctx.beginPath();ctx.moveTo(cx+10,cy+36);ctx.lineTo(cx+cw-10,cy+36);ctx.stroke();
  ctx.font='12px "IBM Plex Mono","Noto Sans KR",monospace';
  ROWS.forEach((row,i)=>{const st=2.4+i*.14,y=cy+56+i*rh;const ra=t<st?Math.min(1,(t-.3-i*.08)/.3):Math.max(0,1-(t-st)/.25);
    ctx.globalAlpha=cardA*Math.max(0,ra);ctx.fillStyle='#2b2925';ctx.fillText(row,cx+14,y)});
  ctx.globalAlpha=1;
  ROWS.forEach((row,i)=>{const st=2.4+i*.14,p=(t-st)/.9;const w=targets[i];if(!w||p<0)return;
    const sx=cx+cw-30,sy=cy+52+i*rh,ex=w.x+w.w/2,ey=w.y+w.h/2;
    if(p<1){const e=1-Math.pow(1-p,3),mx=(sx+ex)/2,my=Math.min(sy,ey)-60;
      const bx=(1-e)*(1-e)*sx+2*(1-e)*e*mx+e*e*ex,by=(1-e)*(1-e)*sy+2*(1-e)*e*my+e*e*ey;
      ctx.fillStyle='#ff8a3d';ctx.beginPath();ctx.arc(bx,by,4,0,7);ctx.fill()}
    else if(p<2.2){const q=(p-1)/1.2;ctx.strokeStyle=`rgba(255,138,61,${1-q})`;ctx.lineWidth=2;ctx.beginPath();ctx.arc(ex,ey,6+q*26,0,7);ctx.stroke()}
  });
}
let last=performance.now();
function frame(now){
  const dt=Math.min(.05,(now-last)/1000);last=now;
  if(playing){hour+=dt*2.2;if(hour>=24)hour-=24;timeEl.value=hour}
  if(introT<99){introT+=dt;
    headline.parentNode.style.opacity=introT<4.2?0:1;
    if(introT>5.6)introT=99}
  const s=sky(hour);
  const g=ctx.createLinearGradient(0,0,0,ground);g.addColorStop(0,rgb(s.top));g.addColorStop(1,rgb(s.bot));
  ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
  if(weather!=='clear'){ctx.fillStyle=`rgba(110,118,132,${weather==='cloud'?.35:.45})`;ctx.fillRect(0,0,W,ground)}
  // 별
  if(s.dark>.6&&weather==='clear'){const r=rng(3);ctx.fillStyle=`rgba(255,255,255,${(s.dark-.6)*1.6})`;for(let i=0;i<70;i++)ctx.fillRect(r()*W,r()*ground*.7,1.3,1.3)}
  // 해 / 달
  const arc=(t)=>({x:W*(.06+.88*t),y:ground-Math.sin(Math.PI*t)*ground*.82});
  if(hour>5.6&&hour<19){const p=arc((hour-5.6)/13.4);const sa=weather==='clear'?1:.35;
    const sg=ctx.createRadialGradient(p.x,p.y,0,p.x,p.y,70);sg.addColorStop(0,`rgba(255,236,190,${.7*sa})`);sg.addColorStop(1,'rgba(255,236,190,0)');ctx.fillStyle=sg;ctx.fillRect(p.x-70,p.y-70,140,140);
    ctx.fillStyle=`rgba(255,243,214,${sa})`;ctx.beginPath();ctx.arc(p.x,p.y,16,0,7);ctx.fill()}
  else{const hh=hour>=19?hour-19:hour+5;const p=arc(hh/10.6);ctx.fillStyle=`rgba(236,240,255,${weather==='clear'?.95:.4})`;ctx.beginPath();ctx.arc(p.x,p.y,12,0,7);ctx.fill()}
  drawWeather(s,dt);
  drawCity(s,false);
  drawFlagsNight(s,now);
  // 돋보기: 입력이 없으면 대상 가구를 차례로 찾아다님
  if(finish){finish.t+=dt;lens.r=84+Math.pow(Math.min(1,finish.t/1.1),2.2)*Math.hypot(W,H)}
  else if(now/1000-lastMove>2.5&&introT>=5){wanderT+=dt;if(wanderT>2.6){wanderT=0;wanderI=(wanderI+1)%targets.length}
    const w=targets[wanderI];if(w){lens.tx=w.x+w.w/2+20;lens.ty=w.y+w.h/2-10}}
  lens.x+=(lens.tx-lens.x)*Math.min(1,dt*(finish?0:6));lens.y+=(lens.ty-lens.y)*Math.min(1,dt*(finish?0:6));
  if(introT>=4.6||finish)drawLens(now);
  drawIntro(introT);
  const f=fmt(hour);clock.innerHTML=`${f.t}<small>${f.ap}</small>`;
  if(finish&&finish.t>1.2&&document.getElementById('done').hidden){document.getElementById('done').hidden=false;document.getElementById('hud').hidden=true}
  requestAnimationFrame(frame);
}
timeEl.value=hour;
timeEl.addEventListener('input',()=>{hour=+timeEl.value;playing=false;setPlay()});
const playBtn=document.getElementById('play');
function setPlay(){playBtn.setAttribute('aria-pressed',playing);playBtn.textContent=playing?'❚❚ 멈춤':'▶ 하루 흐름'}
playBtn.onclick=()=>{playing=!playing;setPlay()};
document.querySelectorAll('[data-w]').forEach(b=>b.onclick=()=>{weather=b.dataset.w;document.querySelectorAll('[data-w]').forEach(o=>o.setAttribute('aria-pressed',o===b))});
// 끌어서 시간 이동 + 돋보기 이동
let drag=null;
cv.addEventListener('pointermove',e=>{const r=cv.getBoundingClientRect(),x=e.clientX-r.left,y=e.clientY-r.top;lens.tx=x;lens.ty=y;lastMove=performance.now()/1000;
  if(drag){hour=(drag.h+(x-drag.x)/W*14+24)%24;timeEl.value=hour}});
cv.addEventListener('pointerdown',e=>{drag={x:e.clientX-cv.getBoundingClientRect().left,h:hour};playing=false;setPlay();cv.setPointerCapture(e.pointerId)});
cv.addEventListener('pointerup',()=>drag=null);cv.addEventListener('pointercancel',()=>drag=null);
document.getElementById('replay').onclick=()=>{introT=0;lens.r=84};
// 로그인 → 입력 확인 후 돋보기가 화면 전체로 커지며 지도 화면으로 전환
const DEMO=false; // 전환 연출(약 1.3초) 뒤 login.do 로 실제 전송 (2026-11-27)
const form=document.getElementById('login'),idI=document.getElementById('loginId'),pwI=document.getElementById('pw'),go=document.getElementById('go'),save=document.getElementById('saveId');
try{const v=localStorage.getItem('itgeo.loginId');if(v){idI.value=v;save.checked=true}}catch(e){}
function err(input,box,text){input.classList.toggle('err',!!text);document.getElementById(box).textContent=text||'';return !text}
idI.addEventListener('input',()=>err(idI,'idMsg',''));pwI.addEventListener('input',()=>err(pwI,'pwMsg',''));
pwI.addEventListener('keyup',e=>{if(e.getModifierState&&e.getModifierState('CapsLock'))err(pwI,'pwMsg','Caps Lock이 켜져 있습니다.')});
form.addEventListener('submit',e=>{e.preventDefault();
  const ok1=err(idI,'idMsg',idI.value.trim()?'':'사원번호를 입력해 주세요.'),ok2=err(pwI,'pwMsg',pwI.value?'':'비밀번호를 입력해 주세요.');
  if(!ok1){idI.focus();return} if(!ok2){pwI.focus();return}
  try{save.checked?localStorage.setItem('itgeo.loginId',idI.value.trim()):localStorage.removeItem('itgeo.loginId')}catch(e){}
  go.disabled=true;go.textContent='확인 중…';introT=99;headline.parentNode.style.opacity=1;finish={t:0};
  if(!DEMO)setTimeout(()=>form.submit(),1300);
  if(innerWidth<=860)scene.scrollIntoView({behavior:'smooth'})});
document.getElementById('back').onclick=()=>{finish=null;lens.r=84;go.disabled=false;go.textContent='로그인';pwI.value='';document.getElementById('done').hidden=true;document.getElementById('hud').hidden=false;introT=reduce?99:0};
requestAnimationFrame(frame);
})();
