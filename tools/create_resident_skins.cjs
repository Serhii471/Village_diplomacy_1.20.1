// Convert the generated civilian atlas to exact Minecraft UVs, retain the supplied
// human head, and derive consistent regional/work clothes and identity palettes.
const fs=require('fs'),path=require('path');
let sharp;try{sharp=require('sharp');}catch{sharp=require(path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp'));}
const out='prototypes/resident-skins';
const resource='src/main/resources/assets/villagediplomacy/textures/entity/resident';
const guardResource='src/main/resources/assets/villagediplomacy/textures/entity/guard';
const palettes={plains:{shirt:[222,207,168],vest:[113,68,49]},mountain:{shirt:[176,188,194],vest:[63,78,94]},forest:{shirt:[158,174,130],vest:[62,89,52]}};
const cap=n=>Math.max(0,Math.min(255,Math.round(n)));
function faceColor(rgb,face){
 if(!face)return rgb;
 let [r,g,b]=rgb;let max=Math.max(r,g,b),min=Math.min(r,g,b);
 if(max-min<9)return rgb;
 if(r>g&&g>b){
  if(max<125){const target=face===1?[39,30,25]:[150,115,53];return target.map(n=>cap(n*(.48+max/115)));}
  const target=face===1?[139,94,65]:[206,162,120];return target.map(n=>cap(n*max/190));
 }
 return rgb;
}
function project(png,name){return {meta:{format_version:'5.0',model_format:'skin',box_uv:true},name,resolution:{width:64,height:64},skin_model:'steve',skin_pose:'natural',textures:[{name:name+'.png',id:'0',width:64,height:64,uv_width:64,uv_height:64,source:'data:image/png;base64,'+png.toString('base64')}]};}
(async()=>{
 fs.mkdirSync(out,{recursive:true});fs.mkdirSync(resource,{recursive:true});
 const source=await sharp(out+'/source/civilian-generated.png').resize(64,64,{kernel:'nearest'}).ensureAlpha().raw().toBuffer();
 const head=await sharp('prototypes/guard-skins/guard_base.png').ensureAlpha().raw().toBuffer();
 const base=Buffer.from(source);
 // Opaque only on the six base cuboids. Generated edge pixels in unused/outer
 // UV regions must never become holes or accidental extra clothing layers.
 const rects=[[0,8,32,8],[8,0,16,8],[16,20,24,12],[20,16,16,4],[40,20,16,12],[44,16,8,4],[0,20,16,12],[4,16,8,4],[16,52,16,12],[20,48,8,4],[32,52,16,12],[36,48,8,4]];
 for(let y=16;y<64;y++)for(let x=0;x<64;x++){
  const k=(y*64+x)*4,opaque=rects.some(([rx,ry,w,h])=>x>=rx&&x<rx+w&&y>=ry&&y<ry+h);
  if(!opaque){base[k+3]=0;continue;}
  if(source[k+3]<128){base[k]=88;base[k+1]=65;base[k+2]=45;}
  base[k+3]=255;
 }
 head.copy(base,0,0,4096);
 // Keep the source's optional hat region intact. Clothing overlays are clear.
 const records=[];
 for(const [region,palette]of Object.entries(palettes))for(const job of ['worker','smith','scholar'])for(let face=0;face<3;face++){
  const raw=Buffer.from(base);
  for(let y=0;y<64;y++)for(let x=0;x<64;x++){
   const k=(y*64+x)*4;if(!raw[k+3])continue;
   let rgb=[raw[k],raw[k+1],raw[k+2]];
   if(y<16)rgb=faceColor(rgb,face);
   else{
    const [r,g,b]=rgb;
    const hand=(y>=28&&y<32&&x>=40&&x<56)||(y>=60&&x>=32&&x<48);
    const collar=x>=22&&x<=25&&y>=20&&y<=22;
    if(hand||collar){if(r>g&&g>b)rgb=faceColor([Math.max(150,r),Math.max(112,g),Math.max(80,b)],face);}
    else if(Math.max(r,g,b)>155&&r-b<100)rgb=palette.shirt.map(n=>cap(n*(.7+Math.max(r,g,b)/850)));
    else if(y<32&&x>=16&&x<40&&r>g&&g>b)rgb=palette.vest.map(n=>cap(n*(.55+r/200)));
   }
   rgb.forEach((n,i)=>raw[k+i]=n);
  }
  const put=(x,y,c)=>{const k=(y*64+x)*4;if(raw[k+3])c.forEach((n,i)=>raw[k+i]=cap(n));};
  if(job==='smith')for(let y=23;y<31;y++)for(let x=21;x<27;x++)put(x,y,[58+(x%2)*6,54+(y%2)*5,48]);
  if(job==='scholar')for(let y=21;y<30;y++)for(let x=20;x<28;x++){
   put(x,y,palette.vest.map(n=>n*.75));if(x===23||x===24)put(x,y,[181,153,86]);
  }
  const name=`${region}_${job}_${face}`;
  const png=await sharp(raw,{raw:{width:64,height:64,channels:4}}).png().toBuffer();
  fs.writeFileSync(`${resource}/${name}.png`,png);fs.writeFileSync(`${out}/${name}.png`,png);
  fs.writeFileSync(`${out}/${name}.bbmodel`,JSON.stringify(project(png,name),null,2));
  records.push({name,width:64,height:64,region,job,face});
 }
 // Recruited soldiers use exactly the same head palette as their civilian UUID.
 for(const type of ['base','plains','mountain','forest'])for(const face of [1,2]){
  const raw=await sharp(`${guardResource}/guard_${type}.png`).ensureAlpha().raw().toBuffer();
  for(let y=0;y<16;y++)for(let x=0;x<64;x++){
   const k=(y*64+x)*4;let c=faceColor([raw[k],raw[k+1],raw[k+2]],face);c.forEach((n,i)=>raw[k+i]=n);
  }
  const png=await sharp(raw,{raw:{width:64,height:64,channels:4}}).png().toBuffer();
  fs.writeFileSync(`${guardResource}/guard_${type}_face_${face}.png`,png);
 }
 fs.writeFileSync(out+'/manifest.json',JSON.stringify({source:'source/civilian-generated.png',method:'Built-in ImageGen edit, nearest-neighbour UV fitting, source-head preservation and deterministic palette/outfit variants',headSource:'../guard-skins/guard_base.png',variants:records},null,2));
 console.log(`Prepared ${records.length} civilian textures/projects and 8 matching guard identity textures`);
})();
