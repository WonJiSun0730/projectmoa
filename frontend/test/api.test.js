import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {api,resetCsrf} from '../src/api.js';
const originalFetch=globalThis.fetch;
after(()=>{globalThis.fetch=originalFetch;});
test('JSON mutations include the server CSRF token and session credentials',async()=>{
 resetCsrf();const calls=[];
 globalThis.fetch=async(url,options)=>{calls.push({url,options});return Response.json(url.endsWith('/csrf')?{token:'masked-token',headerName:'X-CSRF-TOKEN'}:{id:5});};
 assert.deepEqual(await api('/posts',{method:'POST',body:{content:'안녕'}}),{id:5});
 assert.equal(calls[0].url,'/api/auth/csrf');assert.equal(calls[1].options.headers['X-CSRF-TOKEN'],'masked-token');assert.equal(calls[1].options.credentials,'same-origin');assert.equal(calls[1].options.headers['Content-Type'],'application/json');assert.equal(JSON.parse(calls[1].options.body).content,'안녕');
});
test('multipart uploads preserve browser-generated Content-Type boundaries',async()=>{
 resetCsrf();let request;
 globalThis.fetch=async(url,options)=>{if(url.endsWith('/csrf'))return Response.json({token:'t',headerName:'X-CSRF-TOKEN'});request=options;return new Response(null,{status:204});};
 const data=new FormData();data.append('files',new Blob(['test'],{type:'image/png'}),'test.png');assert.equal(await api('/posts/1/media',{method:'POST',body:data}),null);assert.equal(request.body,data);assert.equal(request.headers['Content-Type'],undefined);
});
test('server errors retain status and user-facing message',async()=>{
 globalThis.fetch=async()=>Response.json({message:'접근 권한이 없습니다.'},{status:403});
 await assert.rejects(()=>api('/insights'),e=>e.status===403&&e.message==='접근 권한이 없습니다.');
});
test('reset after authentication changes requests a new CSRF token',async()=>{
 resetCsrf();let tokenCalls=0;
 globalThis.fetch=async(url)=>{if(url.endsWith('/csrf')){tokenCalls++;return Response.json({token:'t'+tokenCalls,headerName:'X-CSRF-TOKEN'});}return new Response(null,{status:204});};
 await api('/auth/logout',{method:'POST'});await api('/auth/logout',{method:'POST'});assert.equal(tokenCalls,1);resetCsrf();await api('/auth/login',{method:'POST',body:{email:'a'}});assert.equal(tokenCalls,2);
});
