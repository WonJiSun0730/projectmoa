let csrf;
async function token() {
  if(!csrf){const r=await fetch('/api/auth/csrf',{credentials:'same-origin'});if(!r.ok)throw new Error('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');csrf=await r.json();}
  return csrf;
}
export function resetCsrf(){csrf=undefined;}
export async function api(url,{method='GET',body,...options}={}) {
  const headers={};
  if(method!=='GET'){const t=await token();headers[t.headerName]=t.token;}
  if(body && !(body instanceof FormData))headers['Content-Type']='application/json';
  let response;
  try{response=await fetch('/api'+url,{...options,method,headers,credentials:'same-origin',body:body instanceof FormData?body:body?JSON.stringify(body):undefined});}
  catch(e){if(e.name==='AbortError')throw e;throw new Error('서버에 연결하지 못했습니다. 백엔드 실행 상태를 확인해 주세요.');}
  if(!response.ok){let data;try{data=await response.json();}catch{data={};}const e=new Error(data.message||'요청을 처리하지 못했습니다. 다시 시도해 주세요.');e.status=response.status;throw e;}
  return response.status===204?null:response.json();
}
