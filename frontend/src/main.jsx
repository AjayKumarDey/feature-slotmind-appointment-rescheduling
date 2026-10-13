import React, { useState, useEffect, useCallback, useRef } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';
const labels = { INITIAL:'Manual', PROVIDER_OFFLINE:'Doctor offline', SLOT_CANCELLED:'Slot cancelled', CONFIRMED:'Confirmed', RESCHEDULE_PENDING:'Needs rescheduling', RESCHEDULED:'Rescheduled', COMPLETED:'Completed', RULE_BASED:'Rule-based', RULE_FALLBACK:'Rule fallback', AI:'AI', FOLLOW_UP:'Follow-up', URGENT:'Urgent', ROUTINE:'Routine' };
const label = x => labels[x] || x?.replaceAll('_',' ').toLowerCase();
const time = x => x ? new Date(x).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'}) : '—';
const date = x => x ? new Date(x).toLocaleDateString([], {day:'numeric',month:'short'}) : '—';
const day = x => x?.slice(0,10);
async function api(path, method='GET', body) {
  const res = await fetch(path, {method, headers: body ? {'Content-Type':'application/json'} : undefined, body:body ? JSON.stringify(body):undefined});
  const data = await res.json().catch(()=>({}));
  if (!res.ok) throw new Error([data.message || `Request failed (${res.status})`, ...(data.errors || [])].join(' · '));
  return data;
}
function App() {
  const [data,setData]=useState({appointments:[],providers:[],slots:[],suggestions:[],jobs:[]});
  const [strategy,setStrategy]=useState('ai');const [strategies,setStrategies]=useState(['ai','rule']);const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');const [notice,setNotice]=useState('');const [busy,setBusy]=useState('');
  const [tab,setTab]=useState('queue');const [selectedDay,setSelectedDay]=useState('');
  const [name,setName]=useState('');const [type,setType]=useState('ROUTINE');
  const [providerId,setProviderId]=useState('PRV-004');const [slotId,setSlotId]=useState('');
  const loadingRef=useRef(false);
  const load=useCallback(async()=>{
    if(loadingRef.current)return;loadingRef.current=true;
    try {
      const [appointments,providers,slots,suggestions,jobs,config]=await Promise.all(['/appointments','/providers','/slots','/suggestions','/reschedule-jobs','/scheduling/strategy'].map(p=>api(p)));
      setData({appointments,providers,slots,suggestions,jobs});setStrategy(config.strategy);setStrategies(config.availableStrategies);setError('');
      setSelectedDay(current=>current || slots[0]?.startTime.slice(0,10) || '');
    } catch(e){setError(e.message);}finally{setLoading(false);loadingRef.current=false;}
  },[]);
  useEffect(()=>{load();const id=setInterval(load,2000);return()=>clearInterval(id);},[load]);
  useEffect(()=>{if(data.providers.length && !data.providers.some(p=>p.id===providerId && p.status!=='OFFLINE'))setProviderId(data.providers.find(p=>p.status!=='OFFLINE')?.id || '');},[data.providers,providerId]);
  const act=async(key,fn,message)=>{if(busy)return;setBusy(key);setError('');setNotice('');try{await fn();setNotice(message);await load();}catch(e){setError(e.message);}finally{setBusy('');}};
  const providerMap=Object.fromEntries(data.providers.map(p=>[p.id,p]));const slotMap=Object.fromEntries(data.slots.map(s=>[s.id,s]));
  const pending=data.appointments.filter(a=>a.status==='RESCHEDULE_PENDING');
  const activeJobs=data.jobs.filter(j=>['QUEUED','RUNNING'].includes(j.status));
  const bookable=data.slots.filter(s=>s.providerId===providerId && s.status==='OPEN' && providerMap[s.providerId]?.status!=='OFFLINE' && new Date(s.startTime)>new Date());
  useEffect(()=>{if(!bookable.some(s=>s.id===slotId))setSlotId(bookable[0]?.id || '');},[data.slots,providerId,slotId]);
  const decide=(s,status)=>act(s.id,()=>api(`/suggestions/${s.id}`,'PATCH',{status}),status==='ACCEPTED'?'Appointment moved. The new slot is booked.':'Suggestion rejected. The original booking stays in place.');
  const propose=a=>act(a.id,()=>api(`/appointments/${a.id}/suggest`,'POST'),'Suggestion ready. Confirm with the patient before accepting.');
  function Suggestions({appointment}){
    const suggestions=data.suggestions.filter(s=>s.appointmentId===appointment.id && s.status==='PENDING');
    const job=data.jobs.filter(j=>j.appointmentId===appointment.id).at(-1);
    return <div className="suggestions">{suggestions.length ? suggestions.map(s=>{
      const slot=slotMap[s.recommendedSlotId];const provider=providerMap[slot?.providerId];
      const stale=slot?.status!=='OPEN' || provider?.status==='OFFLINE';
      return <div className="suggestion" key={s.id}>
        <div className="suggestion-top"><span className={`badge ${s.triggerReason==='INITIAL'?'manual':'auto'}`}>{label(s.triggerReason)}</span><span className="source">{label(s.strategySource)} · <span title="An advisor score, not a guaranteed probability">{Math.round(s.confidence*100)}% confidence</span></span></div>
        <strong>{date(slot?.startTime)} · {time(slot?.startTime)} · {provider?.name || s.alternateProviderId}</strong>
        <p>{s.reasoning}</p>{stale && <p className="warning">This replacement is no longer available. Reject and request another suggestion.</p>}
        <div className="actions"><button className="primary" disabled={!!busy||stale} onClick={()=>decide(s,'ACCEPTED')}>{busy===s.id?'Updating…':'Accept & rebook'}</button><button disabled={!!busy} onClick={()=>decide(s,'REJECTED')}>Reject</button></div>
      </div>;
    }) : <div className="no-option">{job && ['QUEUED','RUNNING'].includes(job.status) ? 'Preparing a replacement suggestion…' : job?.message || 'No pending suggestion. Request one when a suitable slot is available.'}<button disabled={!!busy} onClick={()=>propose(appointment)}>{busy===appointment.id?'Finding options…':'Find replacement'}</button></div>}</div>;
  }
  return <div className="layout">
    <aside className="sidebar"><a className="brand" href="/">S<span>SlotMind</span></a><div className="clinic">CAREBRIDGE CLINICS<br/><span>Front-desk workspace</span></div>
      <nav>{[['queue','Reschedule queue'],['schedule','Day schedule'],['book','Book appointment']].map(([id,text])=><button key={id} className={tab===id?'nav active':'nav'} onClick={()=>setTab(id)}>{text}{id==='queue'&&pending.length>0&&<b>{pending.length}</b>}</button>)}</nav>
      <div className="sidebar-note"><span className="dot"/>Human approval required<p>Recommendations are proposals. Confirm the change with the patient before accepting.</p></div>
    </aside>
    <main><header><div><div className="eyebrow">CAREBRIDGE · COORDINATOR DESK</div><h1>{tab==='queue'?'Keep care moving.':tab==='schedule'?'Today, at a glance.':'Make a new booking.'}</h1><p>{tab==='queue'?'Review replacement appointments and help patients get back on schedule.':tab==='schedule'?'Check doctor availability, appointments, and open slots.':'Choose an available doctor and time.'}</p></div><div className="strategy"><label htmlFor="strategy">Scheduling advisor</label><select id="strategy" value={strategy} disabled={!!busy} onChange={e=>act('strategy',()=>api('/scheduling/strategy','PATCH',{strategy:e.target.value}),'Scheduling strategy updated.')}>{strategies.map(s=><option key={s} value={s}>{s==='ai'?'AI with rule fallback':s==='rule'?'Rule-based':label(s)}</option>)}</select><small>Refreshes every 2 seconds</small></div></header>
      {error&&<div className="alert error" role="alert">{error}<button onClick={load}>Refresh</button></div>}{notice&&<div className="alert success" role="status">{notice}<button aria-label="Dismiss notice" onClick={()=>setNotice('')}>×</button></div>}
      <section className="stats"><div><span>NEEDS RESCHEDULING</span><strong>{pending.length}</strong><small>awaiting a confirmed alternative</small></div><div><span>OPEN SLOTS</span><strong>{data.slots.filter(s=>s.status==='OPEN'&&providerMap[s.providerId]?.status!=='OFFLINE'&&new Date(s.startTime)>new Date()).length}</strong><small>with available doctors</small></div><div><span>RESCHEDULED</span><strong>{data.appointments.filter(a=>a.status==='RESCHEDULED').length}</strong><small>care plans back on track</small></div><div><span>BACKGROUND WORK</span><strong>{activeJobs.length}</strong><small>{activeJobs.length?'generating recommendations':'all caught up'}</small></div></section>
      {loading?<div className="empty">Loading clinic data…</div>:<>
      {tab==='queue'&&<><div className="section-heading"><h2>Approval queue <span>{pending.length}</span></h2><p>Accepting rechecks slot availability.</p></div><div className="queue">{pending.length?pending.map(a=><article className="appointment-card" key={a.id}><div className="patient"><div className="avatar">{a.patientName.split(' ').slice(0,2).map(x=>x[0]).join('')}</div><div><h3>{a.patientName}</h3><span className={`badge ${a.appointmentType.toLowerCase()}`}>{label(a.appointmentType)}</span></div><span className="appointment-id">{a.id.slice(0,12)}</span></div><div className="original"><span>ORIGINAL BOOKING</span><p>{a.providerName} · {date(a.startTime)} · {time(a.startTime)} · {a.durationMinutes} min</p></div><Suggestions appointment={a}/></article>):<div className="empty"><strong>No appointments need rescheduling.</strong><p>Mark a doctor offline or cancel a booked slot to see automatic suggestions.</p><button onClick={()=>setTab('schedule')}>View day schedule</button></div>}</div></>}
      {tab==='schedule'&&<><div className="section-heading"><h2>Doctors & availability</h2></div><section className="provider-grid">{data.providers.map(p=><article className="provider-card" key={p.id}><div className="provider-title"><div className="doctor-icon">+</div><div><h3>{p.name}</h3><p>{label(p.specialty)}</p></div></div><label>Status<select aria-label={`Status for ${p.name}`} value={p.status} disabled={!!busy} onChange={e=>act(p.id,()=>api(`/providers/${p.id}/status`,'PATCH',{status:e.target.value}),e.target.value==='OFFLINE'?'Doctor marked offline. Affected appointments are queued automatically.':'Doctor status updated.')}><option value="AVAILABLE">Available</option><option value="BUSY">Busy</option><option value="OFFLINE">Offline</option></select></label><small>{data.appointments.filter(a=>a.providerId===p.id&&a.status!=='COMPLETED').length} active appointments</small></article>)}</section>
      <div className="section-heading"><h2>Slot calendar</h2><input aria-label="Schedule date" type="date" value={selectedDay} onChange={e=>setSelectedDay(e.target.value)}/></div><div className="table-wrap"><table><thead><tr><th>Time</th><th>Doctor</th><th>Duration</th><th>Status</th><th>Action</th></tr></thead><tbody>{data.slots.filter(s=>day(s.startTime)===selectedDay).sort((a,b)=>a.startTime.localeCompare(b.startTime)).map(s=><tr key={s.id}><td>{time(s.startTime)}</td><td>{providerMap[s.providerId]?.name}</td><td>{s.durationMinutes} min</td><td><span className={`badge ${s.status.toLowerCase()}`}>{label(s.status)}</span></td><td>{s.status!=='CANCELLED'?<button className="text-danger" disabled={!!busy} onClick={()=>act(s.id,()=>api(`/slots/${s.id}/status`,'PATCH',{status:'CANCELLED'}),'Slot cancelled. Any affected appointment is queued automatically.')}>Cancel slot</button>:'—'}</td></tr>)}</tbody></table>{!data.slots.some(s=>day(s.startTime)===selectedDay)&&<div className="empty">No slots on this date.</div>}</div>
      <div className="section-heading"><h2>Appointments</h2></div><div className="table-wrap"><table><thead><tr><th>Patient</th><th>Doctor & time</th><th>Type</th><th>Status</th><th>Action</th></tr></thead><tbody>{data.appointments.map(a=><tr key={a.id}><td>{a.patientName}</td><td>{a.providerName}<small>{date(a.startTime)} · {time(a.startTime)}</small></td><td><span className={`badge ${a.appointmentType.toLowerCase()}`}>{label(a.appointmentType)}</span></td><td>{label(a.status)}</td><td><button disabled={!!busy||a.status==='COMPLETED'} onClick={()=>propose(a)}>{busy===a.id?'Finding…':'Suggest'}</button>{['CONFIRMED','RESCHEDULED'].includes(a.status)&&<button disabled={!!busy} onClick={()=>act(a.id,()=>api(`/appointments/${a.id}/complete`,'PATCH'),'Appointment completed.')}>Complete</button>}</td></tr>)}</tbody></table></div></>}
      {tab==='book'&&<section className="booking-card"><div><span className="eyebrow">NEW APPOINTMENT</span><h2>Find a time that works.</h2><p>Only open, future slots with an online doctor are available for booking.</p></div><form onSubmit={e=>{e.preventDefault();act('book',()=>api('/appointments','POST',{patientName:name,appointmentType:type,providerId,slotId}),'Appointment booked successfully.');}}><label>Patient name<input value={name} onChange={e=>setName(e.target.value)} maxLength={120} required placeholder="Full name"/></label><label>Appointment type<select aria-label="Appointment type" value={type} onChange={e=>setType(e.target.value)}><option value="ROUTINE">Routine</option><option value="URGENT">Urgent</option><option value="FOLLOW_UP">Follow-up</option></select></label><label>Doctor<select aria-label="Doctor" value={providerId} onChange={e=>setProviderId(e.target.value)}>{data.providers.filter(p=>p.status!=='OFFLINE').map(p=><option key={p.id} value={p.id}>{p.name} · {label(p.specialty)}</option>)}</select></label><label>Available slot<select aria-label="Available slot" value={slotId} onChange={e=>setSlotId(e.target.value)} required>{!bookable.length&&<option value="">No available slots</option>}{bookable.map(s=><option key={s.id} value={s.id}>{date(s.startTime)} · {time(s.startTime)} · {s.durationMinutes} min</option>)}</select></label><button className="primary" disabled={!!busy||!slotId||!name.trim()}>{busy==='book'?'Booking…':'Confirm booking'}</button></form></section>}
      <details className="activity"><summary>Background activity · {data.jobs.length} jobs</summary>{data.jobs.length?data.jobs.map(j=><div key={j.id}><span>{j.appointmentId.slice(0,12)} · {label(j.triggerReason)}</span><b>{label(j.status)}</b><p>{j.message||'Waiting for the scheduling advisor'}</p></div>):<p>No automatic triggers yet.</p>}</details>
      </>}
      <footer>SlotMind · CareBridge front desk · Patient approval happens before rebooking.</footer>
    </main>
  </div>;
}
createRoot(document.getElementById('root')).render(<App/>);
