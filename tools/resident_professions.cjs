// Pixel clothing on the standard 64x64 wide-player UV. Accessories occupy
// the hat layer; the original base head/identity pixels are never repainted.
const professions = {
 worker: {title:'Без професії', detail:'Регіональна сорочка та жилет'},
 armorer: {title:'Броняр', detail:'Графітовий фартух, сталевий молот і наплічники', cloth:[57,66,75], trim:[172,189,199], icon:'hammer', cap:[73,82,91]},
 toolsmith: {title:'Інструментальник', detail:'Мідно-коричневий фартух, молот, пояс інструментів', cloth:[117,68,37], trim:[226,177,88], icon:'hammer'},
 weaponsmith: {title:'Зброяр', detail:'Темно-червоний фартух, меч, сталеві манжети', cloth:[92,40,38], trim:[183,194,199], icon:'sword', cap:[57,42,39]},
 librarian: {title:'Бібліотекар', detail:'Бордовий камзол, розгорнута книга, окуляри', cloth:[113,38,52], trim:[230,197,111], icon:'book', cap:[113,38,52], glasses:true},
 cartographer: {title:'Картограф', detail:'Бірюзовий жилет, карта, діагональний ремінь', cloth:[35,105,112], trim:[217,189,132], icon:'map', strap:true, glasses:true},
 cleric: {title:'Священник', detail:'Фіолетовий одяг із золотим оздобленням та аметистом', cloth:[99,52,139], trim:[228,187,81], icon:'gem', cap:[99,52,139]},
 farmer: {title:'Фермер', detail:'Солом’яний головний убір, комбінезон, колосся', cloth:[117,98,46], trim:[229,191,75], icon:'wheat', cap:[200,163,67]},
 fisherman: {title:'Рибалка', detail:'Синій фартух, срібляста риба, в’язана шапка', cloth:[35,81,119], trim:[184,215,219], icon:'fish', cap:[32,65,94]},
 fletcher: {title:'Лучник', detail:'Зелений жилет, стріла та ремінь сагайдака', cloth:[54,99,58], trim:[222,202,151], icon:'arrow', strap:true},
 leatherworker: {title:'Шкіряник', detail:'Рудий шкіряний фартух, латка й виразні шви', cloth:[157,83,37], trim:[235,195,132], icon:'hide'},
 butcher: {title:'М’ясник', detail:'Білий фартух, червоний пояс, сікач і білий ковпак', cloth:[219,215,193], trim:[148,42,40], icon:'cleaver', cap:[226,222,204]},
 mason: {title:'Каменяр', detail:'Сірий робочий фартух із цегляною кладкою', cloth:[112,119,119], trim:[197,133,91], icon:'bricks'},
 shepherd: {title:'Пастух', detail:'Вовняна накидка, овечка та кольоровий пояс', cloth:[187,171,143], trim:[64,119,130], icon:'sheep', cap:[159,143,115]},
 nitwit: {title:'Нероба', detail:'Зелений одяг із контрастними латками', cloth:[70,119,62], trim:[163,127,71], icon:'patch'}
};
// Six-pixel emblems have enough contrast to read on an eight-pixel torso.
const icons = {
 hammer:['.SSSS.','.SSSS.','..WW..','..WW..','..WW..','..WW..'],
 sword:['...S..','..SS..','.SS...','SS....','.WW...','..W...'],
 book:['G..G..','GPPGPP','GPPGPP','GPPGPP','GPPGPP','.GGGG.'],
 map:['PPPPPP','PGPPGP','PPGGPP','PPPGPP','PGPPGP','PPPPPP'],
 gem:['..AA..','.ALLA.','ALLLLA','.ALLA.','..AA..','..GG..'],
 wheat:['.G.G..','..GGG.','.GGG..','..GGG.','...G..','...G..'],
 fish:['..SS..','.SSS.S','SSSSSS','.SSS.S','..SS..','......'],
 arrow:['..SS..','.SSSS.','..WW..','..WW..','.PWP..','P.W.P.'],
 hide:['W....W','WWWWWW','.WWWW.','.WWWW.','WWWWWW','W....W'],
 cleaver:['SSSSS.','SSSSS.','SSSSS.','..W...','..W...','..W...'],
 bricks:['GGDGGG','GGDGGG','DDDDDD','DGGGDG','DGGGDG','DDDDDD'],
 sheep:['.PPPP.','PPPPPP','PPDDPP','PPDDPP','.PPPP.','.D..D.'],
 patch:['GGGGG.','GDGDG.','GGGGG.','GDGDG.','GGGGG.','......']
};
const cap = n=>Math.max(0,Math.min(255,Math.round(n)));
function paintProfession(raw, job, palette) {
 const style=professions[job]; if(!style)throw Error('Unknown profession: '+job);
 if(job==='worker')return;
 const put=(x,y,c)=>{const k=(y*64+x)*4; for(let i=0;i<3;i++)raw[k+i]=cap(c[i]);raw[k+3]=255;};
 const fill=(x,y,w,h,c)=>{for(let v=0;v<h;v++)for(let u=0;u<w;u++)put(x+u,y+v,c.map(n=>n*(.92+((u+v)%3)*.04)));};
 const cloth=style.cloth, trim=style.trim, dark=cloth.map(n=>n*.53);
 // Front bib/apron, sides and back straps wrap the body rather than ending
 // abruptly at a UV seam. The shirt/collar still carries the region palette.
 fill(20,23,8,9,cloth);fill(16,24,4,8,dark);fill(28,24,4,8,dark);
 fill(32,23,8,9,cloth.map((n,i)=>(n+palette.vest[i])*.44));
 fill(21,21,1,3,trim);fill(26,21,1,3,trim);
 fill(33,20,1,9,trim);fill(38,20,1,9,trim);
 fill(16,29,24,1,dark);fill(23,29,2,1,trim);
 // Matching cuffs on both sleeves; leave exposed hands below y=28/60.
 for(const [x,y]of [[40,26],[32,58]]){fill(x,y,16,2,cloth);fill(x,y,16,1,trim);}
 if(job==='armorer')for(const [x,y]of [[44,20],[36,52]]){fill(x,y,4,3,trim);fill(x,y+3,4,1,dark);}
 if(job==='toolsmith')for(let x=20;x<28;x+=2){fill(x,30,1,2,trim);}
 if(job==='leatherworker')for(let y=24;y<32;y+=2){put(20,y,trim);put(27,y,trim);put(32,y,trim);put(39,y,trim);}
 if(job==='cleric')for(const x of [20,27,32,39])fill(x,20,1,12,trim);
 if(job==='shepherd'){fill(20,20,8,3,[218,211,190]);fill(32,20,8,3,[218,211,190]);}
 if(job==='butcher'){fill(16,29,24,2,trim);}
 if(style.strap)for(let y=20;y<32;y++){put(20+Math.min(7,Math.floor((y-20)*.67)),y,[80,53,35]);put(39-Math.min(7,Math.floor((y-20)*.67)),y,[80,53,35]);}
 const colors={S:[205,219,222],W:[170,116,57],G:trim,P:[245,231,194],D:dark,A:[158,103,217],L:[217,175,255]};
 // An outline separates tools/pages from the apron even at normal game distance.
 const icon=icons[style.icon];
 for(let y=0;y<icon.length;y++)for(let x=0;x<6;x++)if(icon[y][x]!=='.'){
  for(const [dx,dy]of [[1,0],[-1,0],[0,1],[0,-1]])if(x+dx>=0&&x+dx<6&&y+dy>=0&&y+dy<6&&icon[y+dy][x+dx]==='.')put(21+x+dx,23+y+dy,dark);
 }
 for(let y=0;y<icon.length;y++)for(let x=0;x<6;x++)if(icon[y][x]!=='.')put(21+x,23+y,colors[icon[y][x]]);
 if(style.cap){
  // Head outer UV: left/front/right/back in the 32..63 strip, plus top.
  fill(32,8,32,3,style.cap);fill(40,0,8,8,style.cap);
  fill(32,10,32,1,job==='farmer'?[133,97,40]:dark);
 }
 if(style.glasses){
  // Eye line is row 3 of the face. Fine brass/black frames use the hat layer.
  const frame=[70,52,37];
  for(const x of [40,41,42,43,44,45,46,47])put(x,11,frame);
  for(const x of [40,42,45,47])put(x,12,frame);
  for(const x of [40,41,42,45,46,47])put(x,13,frame);
 }
}
module.exports={professions,paintProfession};
