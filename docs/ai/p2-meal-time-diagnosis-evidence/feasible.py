# 창 밖 필수 점심 자동 탐지와 직전 아침 기준 가능성 판정
# A: 아침 고정·장소 여유 0 압축 시 가장 이른 점심
# B: 아침을 설정 시각 -30분으로 당긴 경우의 A(장소 여유 0 압축 포함)
import json,glob,os,sys
CFG={'BREAKFAST':480,'LUNCH':720};TOL=30
def m(t):h,mi,*_=t.split(':');return int(h)*60+int(mi)
def hm(x):return f"{x//60:02d}:{x%60:02d}"
for raw in sys.argv[1:]:
    print('##',raw)
    for f in sorted(glob.glob(raw+'/*.json')):
        d=json.load(open(f))
        if not d.get('success'):continue
        days=d['data']['planDays'];last=max(x['dayNumber'] for x in days)
        for day in days:
            s=[x for x in day['schedules'] if x['courseType']!='MEDICATION']
            for li,x in enumerate(s):
                if x['scheduleType']!='LUNCH' or abs(m(x['startTime'])-CFG['LUNCH'])<=TOL:continue
                pm=max([i for i in range(li) if s[i]['scheduleType']=='BREAKFAST'],default=None)
                if pm is None:print(' ',os.path.basename(f),day['dayNumber'],'NO_PREV_MEAL');continue
                def tight(bend):
                    t=bend
                    for y in s[pm+1:li+1]:
                        t+=y['travelMinutes'] or 0
                        if y is x:return t
                        t+=y['stayMinutes']
                bst=s[pm]['stayMinutes'];A=tight(m(s[pm]['startTime'])+bst);B=tight(CFG['BREAKFAST']-TOL+bst)
                lat=CFG['LUNCH']+TOL
                cls='NORMALIZER_DEFECT' if A<=lat else ('FEASIBLE_IF_PREV_MEAL_EARLIER' if B<=lat else 'INFEASIBLE_ORDER_TRAVEL')
                print(f"  {os.path.basename(f)[:-5]} d{day['dayNumber']} lunch {x['startTime'][:5]} A {hm(A)} slack {m(x['startTime'])-A} B {hm(B)} => {cls}")
