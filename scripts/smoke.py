#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Exercise only an explicitly disposable local test deployment; never print credentials."""
import argparse, json, urllib.request, urllib.error, http.cookiejar, secrets, uuid, os
from pathlib import Path
from datetime import datetime, timedelta, timezone
p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:8118');p.add_argument('--env',default='.env');p.add_argument('--allow-test-writes',action='store_true');p.add_argument('--verify-persistence',action='store_true');args=p.parse_args()
assert args.base.startswith(('http://127.0.0.1:','http://localhost:')),'Only isolated local verification is supported'
root=Path(__file__).resolve().parents[1];state=root/'.smoke-state.json'
env=dict(line.split('=',1) for line in Path(args.env).read_text().splitlines() if '=' in line and not line.startswith('#'));count=0
class Client:
    """Same-origin test session with CSRF. 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    def __init__(self,name,password):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.csrf=None
        self.get_csrf();self.call('/auth/login','POST',{'username':name,'password':password})
    def get_csrf(self): self.csrf=self.call('/auth/csrf')
    def call(self,path,method='GET',data=None,status=200,code=None,csrf=True):
        global count
        headers={'Content-Type':'application/json'}
        if self.csrf and csrf:headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(args.base+'/api'+path,method=method,headers=headers,data=json.dumps(data).encode() if data is not None else None)
        try:
            with self.opener.open(req,timeout=30) as res:actual=res.status;value=json.load(res)
        except urllib.error.HTTPError as e:actual=e.code;value=json.load(e)
        assert actual==status,f'{method} {path}: expected {status}, got {actual}, code={value.get("code") if isinstance(value,dict) else "unknown"}'
        if code:assert value.get('code')==code,f'{path}: wrong error code'
        count+=1;return value
admin=Client('admin',env['ADMIN_PASSWORD'])
def key():return str(uuid.uuid4())
def report():print(json.dumps({'checks':count,'result':'PASS','mode':'persistence' if args.verify_persistence else 'fresh-mysql'}))
if args.verify_persistence:
    v=json.loads(state.read_text());author=Client(v['users']['author'],v['password']);director=Client(v['users']['director'],v['password']);owner=Client(v['users']['owner'],v['password'])
    d=author.call('/plans/'+str(v['plan']));assert d['record']['status']=='READY' and len(d['steps'])==2
    a=director.call('/runs/'+str(v['accepted']));assert a['record']['status']=='ACCEPTED';assert all(t['record']['reviewEvidence'] for t in a['tasks'])
    b=director.call('/runs/'+str(v['rolledback']));assert b['record']['status']=='ROLLED_BACK';assert b['tasks'][1]['record']['status']=='FAILED';assert all(t['record']['rollbackReview'] for t in b['tasks'])
    owner.call('/runs/'+str(v['preview']));admin.call('/dashboard');report();raise SystemExit
assert args.allow_test_writes,'Use --allow-test-writes only on a disposable named test deployment'
roles={r['name']:r['id'] for r in admin.call('/admin/roles')};suffix=key()[:8];password='Aa9'+secrets.token_urlsafe(24)
dep=admin.call('/admin/departments','POST',{'name':'TEST 切换协作 '+suffix})['id'];otherdep=admin.call('/admin/departments','POST',{'name':'TEST 部门隔离 '+suffix})['id'];users={};ids={}
for name,role,department in [('author','方案编制员',dep),('director','切换指挥员',dep),('owner','任务执行员',dep),('reviewer','任务核验员',dep),('other','任务执行员',dep),('outside','方案编制员',otherdep)]:
    users[name]=name+'-'+suffix
    ids[name]=admin.call('/admin/users','POST',{'username':users[name],'displayName':{'author':'TEST 方案编制','director':'TEST 切换指挥','owner':'TEST 执行人员','reviewer':'TEST 独立核验','other':'TEST 未指派人员','outside':'TEST 其他部门'}[name],'password':password,'roleId':roles[role],'departmentId':department,'enabled':True})['id']
clients={n:Client(u,password) for n,u in users.items()};author,director,owner,reviewer,other,outside=[clients[n] for n in ['author','director','owner','reviewer','other','outside']]
input={'requestKey':key(),'code':'TEST-CUT-'+suffix.upper(),'title':'TEST 应用入口切换与恢复演练','category':'MIGRATION','departmentId':dep,'directorId':ids['director'],'systemName':'TEST 隔离业务应用','scope':'TEST 独立验收网络，无生产主机或客户数据','decisionCriteria':'TEST 入口与虚构数据核验完成；失败立即停止并回退','recoveryCriteria':'TEST 恢复原入口，独立核对虚构业务数据与受控清单'}
plan=author.call('/plans','POST',input)['id'];assert author.call('/plans','POST',input)['id']==plan
changed=dict(input,title='TEST 改变同一幂等键的载荷');author.call('/plans','POST',changed,409,'IDEMPOTENCY_CONFLICT')
path='/plans/'+str(plan)
def pd():return author.call(path)
def pc():return {'version':pd()['record']['version'],'requestKey':key(),'note':'TEST 已实际核对方案、人员、依赖及恢复要求'}
def si(title,deps):return {'version':pd()['record']['version'],'requestKey':key(),'title':title,'ownerId':ids['owner'],'reviewerId':ids['reviewer'],'minutes':15,'instructions':'TEST 按受控核对清单执行，记录证据编号和实际结果','verification':'TEST 独立核对入口、数据和记录与验收标准一致','rollback':'TEST 恢复原入口与受控数据，登记恢复依据后独立核验','dependencies':deps}
a=author.call(path+'/steps','POST',si('核对备份与恢复依据',[]));step1=a['steps'][0]['id'];b=author.call(path+'/steps','POST',si('切换应用入口并检查业务',[step1]));step2=b['steps'][1]['id']
author.call(path+'/steps/'+str(step1),'PUT',si('循环引用阻断',[step2]),400,'CYCLIC_DEPENDENCY')
author.call(path+'/steps/'+str(step1)+'?version='+str(pd()['record']['version']),'DELETE',status=409,code='DEPENDENCY_REFERENCED')
outside.call(path,status=403,code='OUT_OF_SCOPE');other.call(path,status=403,code='OUT_OF_SCOPE');owner.call('/admin/users',status=403,code='FORBIDDEN');assert other.call('/plans')['total']==0
author.call(path,'POST',{},status=405)
author.call('/plans','POST',input,status=403,code='FORBIDDEN',csrf=False)
author.call(path+'/commands/submit','POST',pc());admin.call(path+'/commands/approve','POST',pc(),403,'NOT_ASSIGNED');director.call(path+'/commands/approve','POST',pc());author.call(path+'/steps','POST',si('冻结不可编辑',[]),409,'INVALID_STATE')
def ri(mode):return {'version':pd()['record']['version'],'requestKey':key(),'mode':mode,'reference':'TEST-'+mode+'-'+key()[:8],'deadline':(datetime.now(timezone.utc)+timedelta(hours=2)).isoformat()}
director.call(path+'/runs','POST',ri('LIVE'),409,'REHEARSAL_REQUIRED')
def newrun(mode):return director.call(path+'/runs','POST',ri(mode))['record']['id']
def rd(r):return director.call('/runs/'+str(r))
def rc(r):return {'version':rd(r)['record']['version'],'requestKey':key(),'note':'TEST 实际核对证据编号与执行结果，记录用于独立验收'}
def tp(r,n,action):return '/runs/'+str(r)+'/tasks/'+str(rd(r)['tasks'][n]['record']['id'])+'/commands/'+action
def tc(c,r,n,action):return c.call(tp(r,n,action),'POST',rc(r))
def cmd(r,action):return director.call('/runs/'+str(r)+'/commands/'+action,'POST',rc(r))
def complete(r,n):tc(owner,r,n,'start');tc(owner,r,n,'complete');tc(reviewer,r,n,'pass')
accepted=newrun('REHEARSAL');director.call(path+'/runs','POST',ri('REHEARSAL'),409,'ACTIVE_RUN');owner.call(tp(accepted,1,'start'),'POST',rc(accepted),409,'DEPENDENCY_BLOCKED');admin.call(tp(accepted,0,'start'),'POST',rc(accepted),403,'NOT_ASSIGNED');complete(accepted,0);complete(accepted,1);cmd(accepted,'accept');assert rd(accepted)['record']['status']=='ACCEPTED'
live=newrun('LIVE');assert rd(live)['tasks'][0]['record']['status']=='PENDING';complete(live,0);tc(owner,live,1,'start');tc(owner,live,1,'fail');owner.call(tp(live,0,'start'),'POST',rc(live),409,'INVALID_STATE');cmd(live,'abort');owner.call(tp(live,0,'rollback'),'POST',rc(live),409,'ROLLBACK_ORDER');tc(owner,live,1,'rollback');tc(reviewer,live,1,'rollback-pass');director.call('/runs/'+str(live)+'/commands/finish-rollback','POST',rc(live),409,'INCOMPLETE_ROLLBACK');tc(owner,live,0,'rollback');tc(reviewer,live,0,'rollback-pass');cmd(live,'finish-rollback');assert rd(live)['record']['status']=='ROLLED_BACK';outside.call('/runs/'+str(live)+'/report.json',status=403,code='OUT_OF_SCOPE');owner.call('/runs/'+str(live)+'/report.json')
preview=newrun('REHEARSAL');tc(owner,preview,0,'start');tc(owner,preview,0,'complete');assert rd(preview)['tasks'][0]['record']['status']=='VERIFYING'
assert author.call('/plans?search='+suffix+'&size=1&sort=code')['total']==1;assert author.call('/plans?status=READY')['total']==1;assert reviewer.call('/workbench')['tasks'];admin.call('/dashboard');admin.call('/audit');admin.call('/admin/settings');admin.call('/admin/menus');admin.call('/admin/dictionaries');admin.call('/admin/permissions')
private={'password':password,'users':users,'ids':ids,'plan':plan,'accepted':accepted,'rolledback':live,'preview':preview}
fd=os.open(state,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
with os.fdopen(fd,'w') as f:json.dump(private,f)
report()
