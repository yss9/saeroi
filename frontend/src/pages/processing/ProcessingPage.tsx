import { ProcessorRequestList } from '../../features/processing/components/ProcessorRequestList.tsx'
import { ProcessingJobList } from '../../features/processing/components/ProcessingJobList.tsx'
import { useState } from 'react'

export function ProcessingPage() {
  const [tab,setTab]=useState<'jobs'|'quotes'>('quotes')
  return <><nav className="processing-tabs" aria-label="가공 업무 구분"><button className={tab==='jobs'?'is-active':''} onClick={()=>setTab('jobs')}>가공 작업</button><button className={tab==='quotes'?'is-active':''} onClick={()=>setTab('quotes')}>견적 요청</button></nav>{tab==='jobs'?<ProcessingJobList/>:<ProcessorRequestList/>}</>
}
