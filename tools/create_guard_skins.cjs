// Deterministic 64x64 skin adaptations and native Blockbench skin projects.
// Usage: node tools/create_guard_skins.cjs [source.png] [output-directory]
const fs = require('fs');
const path = require('path');
let sharp;
try { sharp = require('sharp'); } catch {
  const userHome = process.env.USERPROFILE || process.env.HOME;
  if (!userHome) throw new Error('Install the sharp package to regenerate skins');
  sharp = require(path.join(userHome, '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp'));
}
const source = process.argv[2] || path.resolve('prototypes/guard-skins/guard_base.png');
const out = path.resolve(process.argv[3] || 'prototypes/guard-skins');
const schemes = {
  plains: { label: 'Рівнинне поселення', subtitle: 'Бордовий мундир · сталь · латунь', cloth: [151,48,61], metal: [177,183,183], trim: [197,161,83], dark:[37,38,42] },
  mountain: { label: 'Гірське поселення', subtitle: 'Графітовий мундир · залізо · мідь', cloth:[66,79,94], metal:[135,151,164], trim:[190,120,72], dark:[30,36,44] },
  forest: { label: 'Лісове поселення', subtitle: 'Зелений мундир · шкіра · бронза', cloth:[70,103,59], metal:[124,109,77], trim:[184,163,101], dark:[33,43,32] }
};
const clamp = n => Math.max(0,Math.min(255,Math.round(n)));
const hex = c => '#'+c.slice(0,3).map(n=>clamp(n).toString(16).padStart(2,'0')).join('');
function project(png, id) {
  return {meta:{format_version:'5.0',model_format:'skin',box_uv:true},name:`guard_${id}`,resolution:{width:64,height:64},skin_model:'steve',skin_pose:'natural',textures:[{name:`guard_${id}.png`,id:'0',width:64,height:64,uv_width:64,uv_height:64,mode:'bitmap',saved:true,source:'data:image/png;base64,'+png.toString('base64')}]};
}
function skinPreview(data, cx, cy, back=false) {
  // Render the exported PNG as opaque pixel-sized polygons on the six standard
  // wide-player cuboids. Both views use the actual Minecraft UV rectangles.
  const polys=[]; const a=.43, e=.32, scale=7.2;
  const proj=([x,y,z])=>[cx+(x*Math.cos(a)+z*Math.sin(a))*scale,cy+(-y*Math.cos(e)+(z*Math.cos(a)-x*Math.sin(a))*Math.sin(e))*scale];
  const cubes=[
    {x:-4,y:24,z:-4,w:8,h:8,d:8,u:0,v:0},
    {x:-4,y:12,z:-2,w:8,h:12,d:4,u:16,v:16},
    {x:4,y:12,z:-2,w:4,h:12,d:4,u:40,v:16},
    {x:-8,y:12,z:-2,w:4,h:12,d:4,u:32,v:48},
    {x:0,y:0,z:-2,w:4,h:12,d:4,u:0,v:16},
    {x:-4,y:0,z:-2,w:4,h:12,d:4,u:16,v:48}
  ];
  for(const c of cubes){
    const faces=[
      {w:c.w,h:c.h,u:c.u+c.d+(back?c.w+c.d:0),v:c.v+c.d,shade:1,at:(i,j)=>[c.x+(back?c.w-i:i),c.y+c.h-j,back?c.z+c.d:c.z]},
      {w:c.d,h:c.h,u:c.u+(back?0:c.d+c.w),v:c.v+c.d,shade:.72,at:(i,j)=>[back?c.x:c.x+c.w,c.y+c.h-j,back?c.z+c.d-i:c.z+i]},
      {w:c.w,h:c.d,u:c.u+c.d,v:c.v,shade:1.08,at:(i,j)=>[c.x+i,c.y+c.h,c.z+c.d-j]}
    ];
    for(const f of faces)for(let j=0;j<f.h;j++)for(let i=0;i<f.w;i++){
      const k=((f.v+j)*64+f.u+i)*4;if(!data[k+3])continue;
      const pts=[[i,j],[i+1,j],[i+1,j+1],[i,j+1]].map(([u,v])=>f.at(u,v)).map(([x,y,z])=>back?[-x,y,-z]:[x,y,z]);
      const depth=pts.reduce((s,[x,y,z])=>s+x*Math.sin(a)-z*Math.cos(a)+y*.15,0)/4;
      polys.push({depth,svg:`<polygon points="${pts.map(proj).map(p=>p.map(n=>n.toFixed(2)).join(',')).join(' ')}" fill="${hex([data[k]*f.shade,data[k+1]*f.shade,data[k+2]*f.shade])}"/>`});
    }
  }
  return polys.sort((a,b)=>a.depth-b.depth).map(p=>p.svg).join('');
}
(async()=>{
  fs.mkdirSync(out,{recursive:true});
  const {data:base,info}=await sharp(source).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  if(info.width!==64||info.height!==64)throw Error('Expected a 64x64 wide-player skin');
  const original=await fs.promises.readFile(source);
  const basePath=path.join(out,'guard_base.png');
  if(path.resolve(source)!==basePath)fs.writeFileSync(basePath,original);
  fs.writeFileSync(path.join(out,'guard_base.bbmodel'),JSON.stringify(project(original,'base'),null,2));
  const stats=[];let cards='';let index=0;
  for(const [id,s] of Object.entries(schemes)){
    const raw=Buffer.from(base);
    for(let y=16;y<64;y++)for(let x=0;x<64;x++){
      const k=(y*64+x)*4;if(!base[k+3])continue;
      const [r,g,b]=base.subarray(k,k+3); const max=Math.max(r,g,b),min=Math.min(r,g,b);
      let color;
      if(r>g*1.45&&r>b*1.22&&r-g>18){
        const t=(r*.2126+g*.7152+b*.0722)/70;
        color=s.cloth.map(n=>n*t);
      } else if(g>r*1.14&&g>b*.88&&g-r>12){
        color=s.trim.map(n=>n*(.65+max/350));
      } else if(max-min<20&&max>=76){
        color=s.metal.map(n=>n*max/174);
      } else if(max-min<17&&max<76){
        color=s.dark.map(n=>n*(.45+max/55));
      }
      if(color)color.forEach((n,i)=>raw[k+i]=clamp(n));
    }
    const put=(x,y,c)=>{const k=(y*64+x)*4;if(base[k+3])c.forEach((n,i)=>raw[k+i]=clamp(n));};
    // Small woven badge, away from the existing buttons and belt.
    const badges={plains:['010','111','010','111','010'],mountain:['010','111','101','000','111'],forest:['010','111','110','010','010']};
    badges[id].forEach((line,j)=>[...line].forEach((v,i)=>{if(v==='1')put(20+i,22+j,s.trim);}));
    if(id==='plains'){
      // Brass seam on the sides of the front tabard and sleeve cuffs.
      for(let y=20;y<29;y++)put(27,y,s.trim.map(n=>n*.72));
      for(const [u,v] of [[44,24],[36,56]])for(let x=0;x<4;x++)put(u+x,v,s.trim.map(n=>n*.8));
    }
    if(id==='mountain'){
      // Wool collar and reinforced shoulder rivets.
      for(let x=20;x<28;x++)for(let y=20;y<22;y++)put(x,y,[167+(x%2)*16,158+(x%2)*16,140+(x%2)*16]);
      for(const [u,v] of [[44,20],[36,52]])for(let x=0;x<4;x++)put(u+x,v,s.trim.map(n=>n*(x%3===0?1:.6)));
    }
    if(id==='forest'){
      // Diagonal leather baldric on front and back, over the green tunic.
      for(let j=0;j<9;j++)for(let w=0;w<2;w++){
        put(26-Math.floor(j*.58)+w,20+j,w?[111,80,47]:[65,48,31]);
        put(32+Math.floor(j*.58)+w,20+j,w?[111,80,47]:[65,48,31]);
      }
      put(24,25,s.trim);put(25,25,s.trim);
    }
    // The supplied skin includes an opaque outer cuirass. Carry the regional
    // tabard and insignia onto it so enabling the jacket does not hide the design.
    for(const u of [20,32])for(let j=0;j<9;j++)for(let i=1;i<7;i++){
      const shade=.73+((i+j)%3)*.07;
      put(u+i,36+j,s.cloth.map(n=>n*shade));
    }
    badges[id].forEach((line,j)=>[...line].forEach((v,i)=>{if(v==='1')put(22+i,38+j,s.trim);}));
    if(id==='plains')for(let j=0;j<9;j++)for(const u of [21,26,33,38])put(u,36+j,s.trim.map(n=>n*.78));
    if(id==='mountain')for(let x=20;x<28;x++)for(let y=36;y<38;y++)put(x,y,[167+(x%2)*16,158+(x%2)*16,140+(x%2)*16]);
    if(id==='forest')for(let j=0;j<9;j++)for(let w=0;w<2;w++){
      put(26-Math.floor(j*.58)+w,36+j,w?[111,80,47]:[65,48,31]);
      put(32+Math.floor(j*.58)+w,36+j,w?[111,80,47]:[65,48,31]);
    }
    const png=await sharp(raw,{raw:{width:64,height:64,channels:4}}).png().toBuffer();
    const decoded=await sharp(png).ensureAlpha().raw().toBuffer();
    let changed=0;
    for(let k=0;k<base.length;k+=4){
      if(base[k+3]!==decoded[k+3])throw Error('Alpha changed');
      if(k<64*16*4&&!base.subarray(k,k+4).equals(decoded.subarray(k,k+4)))throw Error('Head changed');
      if(!base.subarray(k,k+4).equals(decoded.subarray(k,k+4)))changed++;
    }
    fs.writeFileSync(path.join(out,`guard_${id}.png`),png);
    fs.writeFileSync(path.join(out,`guard_${id}.bbmodel`),JSON.stringify(project(png,id),null,2));
    stats.push({id,width:64,height:64,changedPixels:changed,headPreserved:true,alphaPreserved:true});
    const left=30+index*440;
    cards+=`<rect x="${left}" y="90" width="420" height="440" rx="18" fill="#1e2831"/><rect x="${left+20}" y="113" width="34" height="4" rx="2" fill="${hex(s.cloth)}"/><text x="${left+20}" y="151" font-size="23" fill="#f1eee6">${s.label}</text><text x="${left+20}" y="179" font-size="14" fill="#a5b3bd">${s.subtitle}</text><ellipse cx="${left+117}" cy="470" rx="68" ry="13" fill="#121a20"/><ellipse cx="${left+306}" cy="470" rx="68" ry="13" fill="#121a20"/>${skinPreview(decoded,left+119,463)}${skinPreview(decoded,left+305,463,true)}<text x="${left+95}" y="505" font-size="13" fill="#a5b3bd">Спереду</text><text x="${left+280}" y="505" font-size="13" fill="#a5b3bd">Ззаду</text>`;
    index++;
  }
  const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="1380" height="595"><rect width="1380" height="595" fill="#111a22"/><g font-family="Segoe UI,Arial,sans-serif"><text x="50" y="49" fill="#f1eee6" font-size="28" font-weight="600">Варта поселень — варіанти скіна</text><text x="50" y="73" fill="#a5b3bd" font-size="14">Спільна основа · індивідуальні кольори та деталі · 64 × 64</text>${cards}<text x="50" y="567" fill="#92a4af" font-size="14">Концепти для погодження. PNG + редаговані проєкти Blockbench. Зовнішні шари вимкнено, як у вихідному перегляді.</text></g></svg>`;
  await sharp(Buffer.from(svg)).png().toFile(path.join(out,'guard_skins_comparison.png'));
  fs.writeFileSync(path.join(out,'validation.json'),JSON.stringify({source:'guard_base.png',model:'Minecraft wide / Steve',variants:stats,verification:'PNG round trip; client and multiplayer evidence is recorded separately in docs/development/guard-skins-verification.json'},null,2));
  console.log(JSON.stringify(stats,null,2));
})();
