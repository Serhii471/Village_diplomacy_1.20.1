// Prepare first: gradlew.bat -PguardSkinCheck writeGuardSkinCheckLaunch --offline
// A repeated run must use a new -PguardSkinCheckDir=build/<fresh-directory>.
// This runner never opens the normal run/ folder or drives mouse/keyboard input.
const fs = require('fs');
const path = require('path');
const {spawn} = require('child_process');
const root = path.resolve(process.argv[2] || 'build/guard-skin-client-check');
const language = process.argv[3] === 'uk_ua' ? 'uk_ua' : 'en_us';
const wait = ms => new Promise(resolve => setTimeout(resolve,ms));
const children = [];
function start(spec, name, directory, username) {
  fs.mkdirSync(directory,{recursive:true});
  const args = [...spec.args];
  if(username) args[args.indexOf('--username')+1]=username;
  const log = fs.openSync(path.join(root,name+'.log'),'w');
  const child = spawn(spec.executable,args,{cwd:directory,env:spec.environment,windowsHide:true,stdio:['ignore',log,log]});
  child.on('error',error=>console.error(name,error.message));
  child.once('exit',code=>console.log(`${name} exited (${code})`));
  children.push(child);
  console.log(`${name}: PID ${child.pid}`);
  return child;
}
async function until(test, label, timeout=360000) {
  const started=Date.now();
  while(!test()) {
    if(Date.now()-started>timeout)throw Error('Timed out: '+label);
    const failures=fs.readdirSync(root).filter(n=>n.endsWith('-failure.txt'));
    if(failures.length)throw Error(failures.map(n=>n+': '+fs.readFileSync(path.join(root,n),'utf8')).join('\n'));
    await wait(500);
  }
  console.log(label);
}
(async()=>{
  if(fs.existsSync(path.join(root,'server-ready.txt'))||fs.existsSync(path.join(root,'server','world')))
    throw Error('Use a fresh guardSkinCheckDir; previous verification artifacts are preserved');
  const server=JSON.parse(fs.readFileSync(path.join(root,'server-launch.json')));
  const client=JSON.parse(fs.readFileSync(path.join(root,'client-launch.json')));
  if(path.resolve(server.directory)!==path.join(root,'server'))
    throw Error('Server launch directory must be the isolated server subdirectory');
  fs.mkdirSync(server.directory,{recursive:true});
  fs.writeFileSync(path.join(server.directory,'eula.txt'),'eula=true\n');
  fs.writeFileSync(path.join(server.directory,'server.properties'),[
    'server-ip=127.0.0.1','server-port=25579','online-mode=false','enforce-secure-profile=false',
    'level-name=world','level-type=minecraft:flat','generate-structures=false','spawn-protection=0',
    'gamemode=spectator','difficulty=peaceful','view-distance=4','simulation-distance=4','max-players=2',
    'enable-status=false','enable-query=false','enable-rcon=false','motd=Disposable guard skin verification'
  ].join('\n')+'\n');
  const first=start(server,'server-first',server.directory);
  await until(()=>fs.existsSync(path.join(root,'server-ready.txt')),'Test server ready');
  for(const username of ['SkinViewerOne','SkinViewerTwo']) {
    const directory=path.join(root,username);
    fs.mkdirSync(directory,{recursive:true});
    fs.writeFileSync(path.join(directory,'options.txt'),[
      'version:3465','lang:'+language,'guiScale:2','renderDistance:4','simulationDistance:5','maxFps:30',
      'pauseOnLostFocus:false','tutorialStep:none','soundCategory_master:0.0','fullscreen:false',
      'gamma:1.0','onboardAccessibility:false','skipMultiplayerWarning:true'
    ].join('\n')+'\n');
    start(client,username,directory,username);
    await wait(3000);
  }
  await until(()=>first.exitCode!==null,'First server stopped for full world reload');
  if(!fs.existsSync(path.join(root,'reload.marker')))throw Error('Initial network checks failed; inspect server-first.log');
  start(server,'server-reload',server.directory);
  await until(()=>fs.existsSync(path.join(root,'server-passed.txt')),'Both viewers passed after server restart');
  await until(()=>children.every(p=>p.exitCode!==null),'All test processes exited cleanly',30000);
  const markers=fs.readdirSync(root).filter(n=>/^SkinViewer.*\.txt$/.test(n));
  fs.writeFileSync(path.join(root,'result.json'),JSON.stringify({passed:true,viewers:2,fullServerRestart:true,markers},null,2));
  console.log('GUARD_SKIN_REAL_MULTIPLAYER_CHECK_PASSED');
})().catch(error=>{
  console.error(error);
  fs.writeFileSync(path.join(root,'runner-failure.txt'),String(error));
  for(const child of children)if(child.exitCode===null)child.kill();
  process.exitCode=1;
});
