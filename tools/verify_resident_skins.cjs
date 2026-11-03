// Verify saved PNG/Blockbench artifacts, independently of the painting recipe.
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict');
let sharp;try{sharp=require('sharp');}catch{sharp=require(path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp'));}
const root='prototypes/resident-skins',resources='src/main/resources/assets/villagediplomacy/textures/entity';
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
(async()=>{
 const manifest=JSON.parse(fs.readFileSync(root+'/manifest.json'));
 assert.equal(manifest.variants.length,135);
 const names=manifest.variants.map(v=>v.name+'.png').sort();
 assert.deepEqual(fs.readdirSync(resources+'/resident').filter(n=>n.endsWith('.png')).sort(),names,'No missing or stale runtime outfits');
 const rects=[[0,8,32,8],[8,0,16,8],[16,20,24,12],[20,16,16,4],[40,20,16,12],[44,16,8,4],[0,20,16,12],[4,16,8,4],[16,52,16,12],[20,48,8,4],[32,52,16,12],[36,48,8,4]];
 const guards=await Promise.all([0,1,2].map(face=>sharp(resources+'/guard/guard_base'+(face?'_face_'+face:'')+'.png').ensureAlpha().raw().toBuffer()));
 const hashes=new Set(),records=[];
 for(const v of manifest.variants){
  const png=fs.readFileSync(root+'/'+v.name+'.png');
  assert(png.equals(fs.readFileSync(resources+'/resident/'+v.name+'.png')),v.name+' runtime differs');
  const project=JSON.parse(fs.readFileSync(root+'/'+v.name+'.bbmodel'));
  assert(Buffer.from(project.textures[0].source.split(',')[1],'base64').equals(png),v.name+' embedded texture differs');
  assert.equal(project.skin_model,'steve');
  const {data,info}=await sharp(png).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  assert.equal(info.width,64);assert.equal(info.height,64);
  for(let y=0;y<64;y++)for(let x=0;x<64;x++){
   const alpha=data[(y*64+x)*4+3];assert(alpha===0||alpha===255,'Soft alpha in '+v.name);
   if(rects.some(([rx,ry,w,h])=>x>=rx&&x<rx+w&&y>=ry&&y<ry+h))assert.equal(alpha,255,'Hole in base cuboid '+v.name);
  }
  for(let y=0;y<16;y++)assert(data.subarray(y*256,y*256+128).equals(guards[v.face].subarray(y*256,y*256+128)),v.name+' changed base head identity');
  const hash=sha(png);assert(!hashes.has(hash),'Duplicate professional/regional appearance '+v.name);hashes.add(hash);
  records.push({name:v.name,sha256:hash,width:info.width,height:info.height,baseHeadMatchesSoldier:true,embeddedTextureMatches:true});
 }
 console.log(JSON.stringify({passed:true,textures:records.length,uniqueAppearances:hashes.size,professions:15,regions:3,faces:3,records},null,2));
})().catch(error=>{console.error(error);process.exitCode=1;});
