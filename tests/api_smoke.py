"""Real HTTP/PostgreSQL contract checks. Run against an isolated test database."""
import json, os, uuid, urllib.request, urllib.error, concurrent.futures
BASE=os.getenv('FITFORGE_API','http://127.0.0.1:8081/api/v1')
def request(method,path,body=None,token=None,key=None,status=200):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    if key: headers['Idempotency-Key']=key
    req=urllib.request.Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
    try:
        response=urllib.request.urlopen(req)
    except urllib.error.HTTPError as error: response=error
    payload=response.read(); result=json.loads(payload) if payload else None
    assert response.status==status,(method,path,response.status,result)
    return result

def run():
    email=str(uuid.uuid4())+'@example.test'; password='correct-horse-password-2026'
    invalid=request('POST','/auth/register',dict(email=email,password='short',displayName='Tester'),status=422)
    assert any(e['field']=='password' for e in invalid['errors'])
    request('POST','/auth/register',dict(email=email,password=password,displayName='Tester',role='ADMIN'),status=400)
    request('POST','/auth/login',dict(email='invalid',password=password),status=422)
    auth=request('POST','/auth/register',dict(email=email,password=password,displayName='Tester',timezone='Asia/Baku'),status=201)
    token=auth['accessToken']
    assert request('POST','/auth/login',dict(email=email,password=password))['user']['id']==auth['user']['id']
    request('POST','/auth/login',dict(email=email,password='wrong-password'),status=401)
    request('PATCH','/me',dict(timezone='invalid-zone'),token,status=422)
    request('PATCH','/me',dict(userId=str(uuid.uuid4())),token,status=400)
    other=request('POST','/auth/register',dict(email=str(uuid.uuid4())+'@example.test',password=password,displayName='Other'),status=201)['accessToken']
    assert request('GET','/me',token=token)['email']==email
    assert request('GET','/dashboard',token=token)['completedWorkouts']==0
    exercise=request('POST','/exercises',dict(name='Bench',muscleGroup='chest',equipment='barbell',trackingType='weight_reps'),token,status=201)
    request('GET','/exercises/'+exercise['id'],token=other,status=404)
    routine=request('POST','/routines',dict(name='Upper',splitType='upper_lower',daysPerWeek=4,estimatedMinutes=60,exercises=[dict(exerciseId=exercise['id'],position=0,defaultSets=2,targetReps=10,defaultWeightKg=None,restSeconds=90)]),token,status=201)
    key=str(uuid.uuid4()); body=dict(routineId=routine['id'])
    workout=request('POST','/workouts',body,token,key,201)
    assert request('POST','/workouts',body,token,key,201)==workout
    request('POST','/workouts',dict(routineId=None),token,key,409)
    request('POST','/workouts',body,token,str(uuid.uuid4()),409)
    path='/workouts/'+workout['id']
    request('GET',path,token=other,status=404)
    ex=workout['exercises'][0]; sets=path+'/exercises/'+ex['id']+'/sets'
    assert ex['sets'][0]['reps'] is None
    request('PATCH','/routines/'+routine['id'],dict(version=1,name='Changed'),token)
    assert request('GET',path,token=token)['routineNameSnapshot']=='Upper'
    request('PATCH',sets+'/'+ex['sets'][0]['id'],dict(version=9,reps=10),token,status=409)
    done=request('PATCH',sets+'/'+ex['sets'][0]['id'],dict(version=1,reps=10,weightKg=60,isCompleted=True),token)
    request('POST',path+'/complete',dict(version=done['workoutVersion'],discardIncompleteSets=False),token,status=409)
    complete=request('POST',path+'/complete',dict(version=done['workoutVersion'],discardIncompleteSets=True),token)
    assert complete['summary']['totalVolumeKg']==600
    assert request('POST',path+'/complete',dict(version=1,discardIncompleteSets=True),token)==complete
    assert request('GET','/dashboard',token=token)['completedWorkouts']==1
    request('PATCH',sets+'/'+ex['sets'][0]['id'],dict(version=complete['version'],reps=12),token,status=409)
    refresh=request('POST','/auth/refresh',dict(refreshToken=auth['refreshToken']))
    request('POST','/auth/refresh',dict(refreshToken=auth['refreshToken']),status=401)
    request('POST','/auth/refresh',dict(refreshToken=refresh['refreshToken']),status=401)
    # Concurrent retries must replay a single persisted operation.
    key=str(uuid.uuid4())
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results=list(pool.map(lambda _:request('POST','/workouts',dict(routineId=None),token,key,201),range(2)))
    assert results[0]['id']==results[1]['id']
    w=results[0]; path='/workouts/'+w['id']
    ex=request('POST',path+'/exercises',dict(version=1,exerciseId=exercise['id']),token,status=201)
    route=path+'/exercises/'+ex['exercise']['id']+'/sets'; key=str(uuid.uuid4()); body=dict(version=2,weightKg=0,reps=5)
    a=request('POST',route,body,token,key,201); assert request('POST',route,body,token,key,201)==a
    request('PATCH',route+'/'+a['set']['id'],dict(version=3,weightKg='10'),token,status=422)
    cancelled=request('POST',path+'/cancel',dict(version=3),token)
    assert request('POST',path+'/cancel',dict(version=1),token)==cancelled
    request('POST',path+'/complete',dict(version=cancelled['version'],discardIncompleteSets=True),token,status=409)
    request('POST','/auth/logout',dict(refreshToken=refresh['refreshToken']),status=204)
    print('PASS: auth, ownership, snapshots, versions, idempotency, concurrency, completion, dashboard, refresh reuse')
if __name__=='__main__': run()
