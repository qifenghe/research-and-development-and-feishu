// Dedicated persistent LOCAL preview only. Creates new labelled software fixtures; never notifies externally.
// Run: RND_PREVIEW_PASSWORD=<configured local bootstrap password> node scripts/verify-rnd-trials.mjs
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {inflateRawSync} from 'node:zlib';
import {createHash, randomUUID} from 'node:crypto';
const base = 'http://127.0.0.1:5183/api/v1';
const password = process.env.RND_PREVIEW_PASSWORD;
assert.ok(password, 'Set RND_PREVIEW_PASSWORD from the local preview bootstrap configuration');
assert.match(fs.readFileSync('backend/src/main/resources/application-preview.yml', 'utf8'), /feishu:\s*\n\s*enabled: false/);
const run = `Task6-${new Date().toISOString().replace(/[:.]/g, '-')}`;
const intermediateName = `${run} 仅工艺内中间产物`;
const output = path.resolve('data/preview', run);
fs.mkdirSync(output, {recursive: true});
const actors = {}, results = [], handles = {run, output, base};
const engineer = 'rnd_engineer', director = 'rnd_director', assistant = 'rnd_assistant';
function verify(label, assertion) {
  try { assertion(); results.push({label, passed: true}); console.log(`PASS ${label}`); }
  catch (error) { results.push({label, passed: false}); throw new Error(`FAIL ${label}: ${error.message}`); }
}
async function request(role, method, url, body) {
  const response = await fetch(base + url, {method, headers: {'Content-Type': 'application/json', Origin: 'http://127.0.0.1:5183',
    ...(actors[role] ? {Authorization: `Bearer ${actors[role].accessToken}`} : {})},
  body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(30000)});
  const json = response.headers.get('content-type')?.includes('json');
  return {status: response.status, headers: response.headers, ...(json ? await response.json() : {bytes: Buffer.from(await response.arrayBuffer())})};
}
async function api(role, method, url, body, expected = '0') {
  const r = await request(role, method, url, body);
  verify(`${role || 'anonymous'} ${method} ${url} => ${expected}`, () => {
    if (expected === 'DENIED') assert.ok(r.status === 401 || r.status === 403 || /FORBIDDEN|ACCESS_DENIED|NOT_ASSIGNED|NOT_OWNER|NOT_ALLOWED|SESSION_TOKEN_REQUIRED/.test(r.code));
    else assert.equal(r.code, expected, `${r.status}: ${r.message}`);
  });
  return r.data;
}
function officeEntries(bytes) {
  const entries = {};
  for (let i = 0; i + 46 < bytes.length; i++) {
    if (bytes.readUInt32LE(i) !== 0x02014b50) continue;
    const size = bytes.readUInt32LE(i + 20), nameLength = bytes.readUInt16LE(i + 28), extra = bytes.readUInt16LE(i + 30), comment = bytes.readUInt16LE(i + 32);
    const name = bytes.subarray(i + 46, i + 46 + nameLength).toString(), offset = bytes.readUInt32LE(i + 42);
    const start = offset + 30 + bytes.readUInt16LE(offset + 26) + bytes.readUInt16LE(offset + 28);
    const compressed = bytes.subarray(start, start + size);
    if (name.endsWith('.xml')) entries[name] = (bytes.readUInt16LE(i + 10) === 8 ? inflateRawSync(compressed) : compressed).toString();
    i += 45 + nameLength + extra + comment;
  }
  return entries;
}
function worksheetRows(entries, sheet) {
  const texts = xml => [...xml.matchAll(/<(?:\w+:)?t(?:\s[^>]*)?>([\s\S]*?)<\/(?:\w+:)?t>/g)].map(m => m[1]).join('');
  const shared = [...(entries['xl/sharedStrings.xml'] || '').matchAll(/<si>([\s\S]*?)<\/si>/g)].map(m => texts(m[1]));
  return [...entries[`xl/worksheets/sheet${sheet}.xml`].matchAll(/<row\b[^>]*>([\s\S]*?)<\/row>/g)].map(row => {
    const values = [];
    for (const cell of row[1].matchAll(/<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
      const column = cell[1].match(/\br="([A-Z]+)\d+"/)?.[1];
      assert.ok(column, 'Worksheet cells must have explicit column references');
      const index = [...column].reduce((n, c) => n * 26 + c.charCodeAt(0) - 64, 0) - 1;
      const value = (cell[2] || '').match(/<v>([\s\S]*?)<\/v>/)?.[1];
      values[index] = /t="s"/.test(cell[1]) ? shared[Number(value)] : /t="inlineStr"/.test(cell[1]) ? texts(cell[2]) : value === undefined ? '' : Number(value);
    }
    return values;
  });
}
async function download(role, url, name) {
  const r = await request(role, 'GET', url);
  verify(`${role} download ${name}`, () => {
    assert.equal(r.status, 200); assert.ok(r.bytes, r.code); assert.equal(r.bytes.subarray(0, 2).toString(), 'PK');
    assert.match(r.headers.get('content-disposition') || '', /attachment/);
    assert.match(r.headers.get('content-type') || '', url.startsWith('/pricing-files/') ? /octet-stream/ : /officedocument/);
  });
  fs.writeFileSync(path.join(output, name), r.bytes);
  return {bytes: r.bytes, entries: officeEntries(r.bytes), text: Object.values(officeEntries(r.bytes)).join('\n')};
}
const major = (i, input, out) => ({id: `major-${i}`, sequence: i, processCode: `M${i}`, processName: i === 1 ? '测试修割' : '测试熟制',
  yieldBasis: 'PRIMARY_INPUT', remark: '软件测试称重损耗，不构成生产标准', inputs: [], outputs: [], steps: [{id: `step-${i}`, sequence: 1,
    stepCode: `S${i}`, stepName: i === 1 ? '测试修割步骤' : '测试熟制步骤', stepType: 'NORMAL', equipment: '软件示例设备',
    instruction: '按软件测试示例记录投入与产出；这些参数不是经验证的生产标准。', parameter1Name: '测试时间', parameter1Value: '10', parameter1Unit: 'min',
    materials: [{id: `mat-${i}`, sequence: 1, materialName: i === 1 ? '测试主料' : intermediateName, materialCode: i === 1 ? 'TEST6-BEEF' : null, materialRole: 'PRIMARY',
      sourceType: i === 1 ? 'EXTERNAL' : 'STEP_OUTPUT', sourceStepOutputId: i === 1 ? null : 'out-1', weightKg: input, materialState: 'SOLID'},
      ...(i === 1 ? [{id: 'aux', sequence: 2, materialName: '测试辅料', materialCode: 'TEST6-SALT', materialRole: 'AUXILIARY', sourceType: 'EXTERNAL', weightKg: 2, materialState: 'SOLID'}] : [])],
    outputs: [{id: `out-${i}`, sequence: 1, outputType: i === 1 ? 'INTERMEDIATE' : 'FINISHED', outputName: i === 1 ? intermediateName : '测试产出', weightKg: out, materialState: 'SOLID', primaryOutput: true, continueFlow: i === 1}],
    controlPoints: [{id: `cp-${i}`, sequence: 1, controlType: 'QUALITY', importance: 'CRITICAL', itemName: '软件测试温度', targetValue: 75,
      lowerLimit: 70, upperLimit: 85, unit: '℃', method: '软件示例探针', measurementTool: '软件示例仪表', frequency: '每批软件测试',
      deviationAction: '隔离并记录偏差，重新测试后由授权人员确认（软件示例）', basisOrRemark: '软件测试，非生产验证标准',
      resolved: true, confirmedBy: '伪造身份', confirmedAt: '2026-09-22T10:00', measurements: [{id: `measurement-${i}`, sequence: 1,
        measuredValue: 76, measuredAt: '2026-09-22T10:00', result: 'PASS', retestResult: 'PASS'}]}]}]});
const plan = () => ({versionNo: 0, status: 'DRAFT', majorProcesses: [major(1, 100, 90), major(2, 90, 72)]});
function saveBody(t) { const {versionNo, name, purpose, variables, conclusion, recommendationReason, qualityScore, qualityNotes, difficulty, plan, plannedData} = t;
  return {versionNo, name, purpose, variables, conclusion, recommendationReason, qualityScore, qualityNotes, difficulty, plan, plannedData}; }
function persist() { fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify({...handles, results, passed: results.filter(r => r.passed).length, total: results.length}, null, 2)); }
try {
  for (const role of [assistant, director, engineer, 'tester', 'finance', 'admin']) {
    const r = await request(null, 'POST', '/auth/login', {username: role, password});
    assert.equal(r.code, '0', `Local configured login failed: ${role}`); actors[role] = r.data;
  }
  const req = await api(assistant, 'POST', '/sample-requests', {productName: `${run} 软件测试专用`, productType: '冷冻即热菜', customerName: '内部软件测试',
    specification: '1kg/袋', applicationScenario: '研发工作台验收', flavorRequirement: '所有参数仅用于软件测试，非生产标准', creatorName: actors[assistant].user.name});
  const approved = await api(director, 'POST', `/sample-requests/${req.id}/approve`, {reviewerName: actors[director].user.name});
  const task = approved.task; handles.taskId = task.id; handles.versionId = approved.version.id;
  await api(director, 'POST', `/rnd-tasks/${task.id}/assign`, {assigneeName: actors[engineer].user.name, productOwnerName: actors[engineer].user.name, dueDate: '2026-12-31'});
  await api(engineer, 'POST', `/rnd-tasks/${task.id}/accept`, {acceptedBy: actors[engineer].user.name});
  const form = await api(engineer, 'POST', `/rnd-tasks/${task.id}/experiment-form/draft`, {operatorName: actors[engineer].user.name,
    summary: `${run} 软件验收，非生产标准`, materials: [{stage: '原料', sequence: 1, materialCode: 'TEST6-BEEF', materialName: '测试主料', weightKg: 100, materialCategory: 'RAW', primaryMaterial: true, utilizationRate: 1}],
    processSteps: [{sequence: 1, processName: '软件测试', beforeWeightKg: 100, afterWeightKg: 72}], finishedOutputWeightKg: 74, finishedOutputQuantity: 74, finishedOutputUnit: '袋'});
  handles.formId = form.id; handles.url = `http://127.0.0.1:5183/admin/rnd/tasks/${task.id}/experiment`; persist();
  console.log(`FIXTURE ${handles.url} form=${form.id}`);
  const fp = `/experiment-forms/${form.id}`, pp = `${fp}/process-plan`, tp = `${fp}/trials`;
  const initial = await api(engineer, 'GET', pp);
  const auditsBefore = await api('admin', 'GET', '/admin/audit-logs');
  const detailBefore = await api(engineer, 'GET', `/rnd-tasks/${task.id}/detail`);
  const previews = ['FORMULA_XLSX', 'SOP_DOCX', 'PRICING_XLSX'];
  for (const type of previews) {
    const empty = await download(engineer, `${pp}/export-preview?versionNo=${initial.versionNo}&artifactType=${type}`, `empty-${type}.${type === 'SOP_DOCX' ? 'docx' : 'xlsx'}`);
    verify(`empty ${type} retains pending actual`, () => assert.match(empty.text, /待填写/));
  }
  const detailAfter = await api(engineer, 'GET', `/rnd-tasks/${task.id}/detail`), auditsAfter = await api('admin', 'GET', '/admin/audit-logs');
  verify('empty previews leave task detail and audit unchanged', () => {assert.deepEqual(detailAfter, detailBefore); assert.deepEqual(auditsAfter, auditsBefore);});
  let a = await api(engineer, 'POST', tp, {name: `${run} A`, purpose: '软件验收', variables: '测试称重', plan: plan(), plannedData: {materialWeightsKg: {'mat-1': 110}, batchYieldTarget: 99}});
  handles.trialA = a.id; const ap = `${tp}/${a.id}`;
  verify('client forged critical confirmation stripped', () => assert.equal(a.plan.majorProcesses[0].steps[0].controlPoints[0].confirmedBy, null));
  const unconfirmed = await api(engineer, 'POST', `${ap}/submission-preview`, {trialVersionNo: a.versionNo, expectedProcessVersionNo: initial.versionNo});
  verify('unconfirmed critical control blocks formal promotion', () => {assert.equal(unconfirmed.checks.ready, false); assert.ok(unconfirmed.checks.errors.some(e => e.code === 'CRITICAL_CONTROL_UNRESOLVED'));});
  let b = await api(engineer, 'POST', `${ap}/copy`, {versionNo: a.versionNo, name: `${run} B`, includeActuals: false});
  handles.trialB = b.id; const bp = `${tp}/${b.id}`; persist();
  verify('copy keeps planned input and clears actual', () => {const m = b.plan.majorProcesses[0].steps[0].materials[0]; assert.equal(m.weightKg, null); assert.equal(b.plannedData.materialWeightsKg[m.id], 110);});
  b.plannedData.materialWeightsKg[b.plan.majorProcesses[0].steps[0].materials[0].id] = 99.8;
  b = await api(engineer, 'PUT', bp, saveBody(b));
  a.purpose = 'A 独立保存'; a = await api(engineer, 'PUT', ap, saveBody(a));
  const ar = await api(engineer, 'GET', ap), br = await api(engineer, 'GET', bp);
  verify('A/B independent save reload planned versus actual', () => {assert.equal(ar.purpose, 'A 独立保存'); assert.equal(ar.plan.majorProcesses[0].steps[0].materials[0].weightKg, 100); assert.equal(br.plannedData.materialWeightsKg[br.plan.majorProcesses[0].steps[0].materials[0].id], 99.8); assert.equal(br.plan.majorProcesses[0].steps[0].materials[0].weightKg, null);});
  await api(engineer, 'PUT', ap, {...saveBody(a), versionNo: a.versionNo - 1}, 'TRIAL_VERSION_CONFLICT');
  await api(engineer, 'POST', `${ap}/control-points/${a.plan.majorProcesses[0].steps[0].controlPoints[0].id}/confirm`, {trialVersionNo: a.versionNo - 1}, 'TRIAL_VERSION_CONFLICT');
  b = await api(engineer, 'POST', `${bp}/archive`, {versionNo: b.versionNo, archived: true});
  await api(engineer, 'POST', `${bp}/submission-preview`, {trialVersionNo: b.versionNo, expectedProcessVersionNo: initial.versionNo}, 'TRIAL_ARCHIVED');
  b = await api(engineer, 'POST', `${bp}/archive`, {versionNo: b.versionNo, archived: false});
  for (const observed of [null, 0]) {
    const nonePlan = {versionNo: 0, status: 'DRAFT', majorProcesses: [major(1, observed, observed)]};
    Object.assign(nonePlan.majorProcesses[0], {yieldBasis: 'NONE', steps: [],
      inputs: observed === null ? [] : [{id: 'none-input', sequence: 1, inputRole: 'PRIMARY', materialCode: 'TEST6-BEEF', materialName: '测试主料', weightKg: observed}],
      outputs: observed === null ? [] : [{id: 'none-output', sequence: 1, outputType: 'QUALIFIED', weightKg: observed}]});
    const none = await api(engineer, 'POST', tp, {name: `${run} NONE-${observed === null ? 'missing' : 'zero'}`, plan: nonePlan});
    const file = await download(engineer, `${tp}/${none.id}/export-preview?versionNo=1&artifactType=PRICING_XLSX`, `none-${observed === null ? 'missing' : 'zero'}.xlsx`);
    verify(`NONE ${observed === null ? 'unobserved remains pending' : 'observed zero remains numeric'}`, () => {
      const row = worksheetRows(file.entries, 2).find(row => row[0] === '1 测试修割');
      assert.equal(row[2], observed === null ? '待填写' : 0); assert.equal(row[3], observed === null ? '待填写' : 0);
      const total = worksheetRows(file.entries, 1).find(row => row[0] === '外部投料合计'); assert.equal(total[4], '待填写');
    });
  }
  const inherited = await api(engineer, 'POST', `${ap}/copy`, {versionNo: a.versionNo, name: `${run} inherited`, includeActuals: true});
  verify('copied measurements marked inherited', () => assert.equal(inherited.inheritedActuals, true));
  await api(engineer, 'POST', `${tp}/${inherited.id}/control-points/${inherited.plan.majorProcesses[0].steps[0].controlPoints[0].id}/confirm`, {trialVersionNo: inherited.versionNo}, 'TRIAL_INHERITED_MEASUREMENT');
  for (const role of ['tester', 'finance']) for (const url of [tp, ap, pp, `${ap}/export-check?versionNo=${a.versionNo}&artifactType=SOP_DOCX`, `${ap}/export-preview?versionNo=${a.versionNo}&artifactType=FORMULA_XLSX`]) await api(role, 'GET', url, undefined, 'DENIED');
  await api(null, 'GET', ap, undefined, 'DENIED');
  for (let i = 0; i < 2; i++) a = await api(engineer, 'POST', `${ap}/control-points/${a.plan.majorProcesses[i].steps[0].controlPoints[0].id}/confirm`, {trialVersionNo: a.versionNo});
  verify('trusted confirmer derives from session', () => assert.equal(a.plan.majorProcesses[0].steps[0].controlPoints[0].confirmedBy, actors[engineer].user.name));
  const incompletePlan = plan(); incompletePlan.majorProcesses[0].steps[0].instruction = null;
  let incomplete = await api(engineer, 'POST', tp, {name: `${run} export-blocked`, plan: incompletePlan});
  const ip = `${tp}/${incomplete.id}`;
  for (let i = 0; i < 2; i++) incomplete = await api(engineer, 'POST', `${ip}/control-points/${incomplete.plan.majorProcesses[i].steps[0].controlPoints[0].id}/confirm`, {trialVersionNo: incomplete.versionNo});
  const incompletePreview = await api(engineer, 'POST', `${ip}/submission-preview`, {trialVersionNo: incomplete.versionNo, expectedProcessVersionNo: initial.versionNo});
  const oldRevision = await api(engineer, 'POST', `${ip}/submit`, {trialVersionNo: incomplete.versionNo, expectedProcessVersionNo: initial.versionNo, previewToken: incompletePreview.previewToken, confirmed: true, idempotencyKey: randomUUID()});
  const oldPath = `${pp}/revisions/${oldRevision.id}`;
  const blocked = await api(engineer, 'GET', `${oldPath}/export-check?artifactType=SOP_DOCX`);
  verify('missing instruction blocks new formal SOP', () => {assert.equal(blocked.ready, false); assert.ok(blocked.issues.some(i => i.path.includes('instruction')));});
  await api(engineer, 'POST', `${oldPath}/artifacts`, {artifactType: 'SOP_DOCX'}, 'PROCESS_EXPORT_NOT_READY');
  const blockedArtifacts = await api(engineer, 'GET', `${oldPath}/artifacts`); verify('blocked generation adds no artifact', () => assert.deepEqual(blockedArtifacts, []));
  const oldFormula = await api(engineer, 'POST', `${oldPath}/artifacts`, {artifactType: 'FORMULA_XLSX'});
  const reopened = await api(engineer, 'POST', `${oldPath}/new-draft`, {changeReason: '软件验收补齐操作说明'});
  const draft = await api(engineer, 'PUT', pp, {...plan(), versionNo: reopened.versionNo});
  let pv = await api(engineer, 'POST', `${ap}/submission-preview`, {trialVersionNo: a.versionNo, expectedProcessVersionNo: draft.versionNo});
  verify('confirmed complete actual flow passes formal gate', () => assert.equal(pv.checks.ready, true));
  const displacedRequest = structuredClone(draft); displacedRequest.majorProcesses[0].processName = '应被保留的完整草稿';
  const newerDraft = await api(engineer, 'PUT', pp, displacedRequest);
  const command = {trialVersionNo: a.versionNo, expectedProcessVersionNo: draft.versionNo, previewToken: pv.previewToken, confirmed: true, idempotencyKey: randomUUID()};
  await api(engineer, 'POST', `${ap}/submit`, command, 'PROCESS_PLAN_VERSION_CONFLICT');
  pv = await api(engineer, 'POST', `${ap}/submission-preview`, {trialVersionNo: a.versionNo, expectedProcessVersionNo: newerDraft.versionNo});
  verify('preview warns about displaced draft', () => assert.equal(pv.differingDraft, true));
  const submit = {...command, expectedProcessVersionNo: newerDraft.versionNo, previewToken: pv.previewToken};
  await api(engineer, 'POST', `${ap}/submit`, {...submit, confirmed: false}, 'PROCESS_SUBMISSION_CONFIRMATION_REQUIRED');
  const rev = await api(engineer, 'POST', `${ap}/submit`, submit); handles.revisionId = rev.id;
  const rp = `${pp}/revisions/${rev.id}`;
  verify('formal main yield uses actuals not planned 99%', () => assert.equal(rev.snapshot.batchYieldPercent, 72));
  verify('intermediate stays an internal output reference without material-master identity', () => {
    const output = rev.snapshot.majorProcesses[0].steps[0].outputs[0];
    const input = rev.snapshot.majorProcesses[1].steps[0].materials[0];
    assert.equal(output.outputName, intermediateName); assert.equal(output.outputType, 'INTERMEDIATE');
    assert.equal(input.sourceType, 'STEP_OUTPUT'); assert.equal(input.sourceStepOutputId, output.id);
    assert.equal(input.materialCode, null); assert.equal(input.formulaMaterialId, null);
  });
  const retry = await api(engineer, 'POST', `${ap}/submit`, submit); verify('retry creates no new revision', () => assert.equal(retry.id, rev.id));
  const displaced = await api(engineer, 'GET', `${rp}/displaced-draft`); verify('displaced draft is recoverable unchanged', () => assert.deepEqual(displaced, newerDraft));
  const source = await api(engineer, 'GET', `${rp}/source`); verify('formal source has submitted trial version only', () => {assert.equal(source.trialId, a.id); assert.equal(source.trialVersionNo, a.versionNo); assert.ok(!('plan' in source));});
  const artifactBefore = await api(engineer, 'GET', `${rp}/artifacts`);
  const auditSnapshot = await api('admin', 'GET', '/admin/audit-logs');
  for (const type of previews) {
    const check = await api(engineer, 'GET', `${ap}/export-check?versionNo=${a.versionNo}&artifactType=${type}`);
    if (type === 'PRICING_XLSX') verify('trial pricing cannot borrow independent finished output', () => assert.equal(check.ready, false));
    const preview = await download(engineer, `${ap}/export-preview?versionNo=${a.versionNo}&artifactType=${type}`, `trial-${type}.${type === 'SOP_DOCX' ? 'docx' : 'xlsx'}`);
    verify(`${type} fixed trial/version label`, () => {assert.match(preview.text, /研发预览/); assert.ok(preview.text.includes(a.name)); assert.ok(preview.text.includes(`V${a.versionNo}`));});
  }
  const artifactAfter = await api(engineer, 'GET', `${rp}/artifacts`), auditAfter = await api('admin', 'GET', '/admin/audit-logs');
  verify('trial preview downloads do not create artifacts or approvals', () => {assert.deepEqual(artifactAfter, artifactBefore); assert.deepEqual(auditAfter, auditSnapshot);});
  await api(engineer, 'GET', `${ap}/export-preview?versionNo=${a.versionNo - 1}&artifactType=SOP_DOCX`, undefined, 'PROCESS_EXPORT_VERSION_CONFLICT');
  const arts = {};
  for (const type of ['FORMULA_XLSX', 'SOP_DOCX']) {
    const check = await api(engineer, 'GET', `${rp}/export-check?artifactType=${type}`); verify(`${type} new formal generation check ready`, () => assert.equal(check.ready, true));
    arts[type] = await api(engineer, 'POST', `${rp}/artifacts`, {artifactType: type});
    const file = await download(engineer, `${rp}/artifacts/${arts[type].id}/download`, `formal-${type}.${type === 'SOP_DOCX' ? 'docx' : 'xlsx'}`);
    verify(`${type} immutable source label and actual 102kg`, () => {
      assert.ok(file.text.includes(a.name)); assert.ok(file.text.includes(`V${a.versionNo}`)); assert.ok(file.text.includes(`R${rev.revisionNo}`));
      assert.match(file.text, /102/); assert.doesNotMatch(file.text, /单价|金额|成本|毛利|税率|税费|利润|自动报价/);
      if (type === 'FORMULA_XLSX') {
        assert.equal(worksheetRows(file.entries, 1).find(r => r[0] === '合计')[4], 102);
        assert.equal(worksheetRows(file.entries, 2).find(r => r[0] === '合计')[4], 100);
        assert.match(file.entries['xl/workbook.xml'], /本次实际投料/); assert.match(file.entries['xl/workbook.xml'], /每100kg折算/);
      }
    });
    arts[type].sha256 = createHash('sha256').update(file.bytes).digest('hex');
  }
  const submittedA = structuredClone(a); a.name = `${run} A later-edit`; a.plan.majorProcesses[0].steps[0].materials[0].weightKg = 101;
  a = await api(engineer, 'PUT', ap, saveBody(a));
  const immutable = await api(engineer, 'GET', rp), immutableSource = await api(engineer, 'GET', `${rp}/source`);
  verify('later trial edits do not change formal source/snapshot', () => {assert.deepEqual(immutable, rev); assert.deepEqual(immutableSource, source);});
  for (const type of ['FORMULA_XLSX', 'SOP_DOCX']) {const file = await download(engineer, `${rp}/artifacts/${arts[type].id}/download`, `immutable-${type}.${type === 'SOP_DOCX' ? 'docx' : 'xlsx'}`); verify(`${type} archived bytes unchanged`, () => assert.equal(createHash('sha256').update(file.bytes).digest('hex'), arts[type].sha256));}
  await api('tester', 'GET', `${rp}/source`, undefined, 'DENIED');
  await api(engineer, 'POST', `${fp}/submit-test`, {testerName: actors.tester.user.name});
  const detail = await api('tester', 'GET', `/rnd-tasks/${task.id}/detail`); const testId = detail.currentTestAssignment?.id || detail.testAssignment?.id; assert.ok(testId); handles.testId = testId;
  await api('tester', 'GET', `${rp}/source`); await api('tester', 'GET', rp);
  await api('tester', 'GET', `${oldPath}/source`, undefined, 'DENIED');
  await api('tester', 'GET', oldPath, undefined, 'DENIED');
  await api('tester', 'GET', `${oldPath}/artifacts/${oldFormula.id}/download`, undefined, 'DENIED');
  for (const role of ['tester', 'finance']) {await api(role, 'GET', `${rp}/displaced-draft`, undefined, 'DENIED'); await api(role, 'GET', ap, undefined, 'DENIED');}
  await api('finance', 'GET', `${rp}/source`, undefined, 'DENIED');
  for (const type of ['FORMULA_XLSX', 'SOP_DOCX']) {await download('tester', `${rp}/artifacts/${arts[type].id}/download`, `tester-${type}.${type === 'SOP_DOCX' ? 'docx' : 'xlsx'}`); await api('tester', 'POST', `${rp}/artifacts`, {artifactType: type}, 'DENIED');}
  for (const [suffix, body] of [['', saveBody(a)], ['/copy', {versionNo: a.versionNo, name: '不应创建'}], ['/archive', {versionNo: a.versionNo, archived: true}], ['/submission-preview', {trialVersionNo: a.versionNo, expectedProcessVersionNo: rev.snapshot.versionNo}]]) await api(engineer, suffix ? 'POST' : 'PUT', ap + suffix, body, 'TRIAL_FORM_LOCKED');
  await api('tester', 'POST', `/test-assignments/${testId}/pass`, {testerName: actors.tester.user.name, comment: '仅软件验收通过，非生产参数验证'});
  await api(engineer, 'PUT', ap, saveBody(a), 'TRIAL_FORM_LOCKED');
  const pricing = await api(assistant, 'POST', `/sample-versions/${approved.version.id}/pricing-files`); handles.pricingId = pricing.id;
  const items = await api(engineer, 'GET', `/pricing-files/${pricing.id}/packaging-items`);
  const explicitItems = items.map((item, i) => ({...item, quantityUnit: i === 0 ? '袋' : '张', quantity: 74, packageSpec: '1kg/袋（软件测试）', conversionRule: '每袋1个，软件测试示例', modificationReason: '补全软件测试包装单位/规格/换算'}));
  assert.ok(explicitItems.length > 0, 'Bootstrap packaging fixture must include real items');
  await api(engineer, 'PUT', `/pricing-files/${pricing.id}/packaging-items`, {items: explicitItems.map(item => ({...item, quantityUnit: null}))}, 'PROCESS_EXPORT_NOT_READY');
  const priced = await api(engineer, 'PUT', `/pricing-files/${pricing.id}/packaging-items`, {items: explicitItems});
  verify('pricing generation does not approve or notify finance', () => assert.equal(priced.status, 'PENDING_PRICING_REVIEW'));
  const pricingFile = await download(engineer, `/pricing-files/${pricing.id}/download`, 'formal-pricing.xlsx');
  verify('pricing exact immutable trial source and independent 74 output', () => {
    assert.ok(pricingFile.text.includes(submittedA.name)); assert.ok(pricingFile.text.includes(`V${submittedA.versionNo}`)); assert.ok(pricingFile.text.includes(`R${rev.revisionNo}`));
    assert.equal(worksheetRows(pricingFile.entries, 1).find(r => r[0] === '外部投料合计')[4], 102);
    assert.equal(worksheetRows(pricingFile.entries, 2).find(r => r[0] === '全流程主料得率 %')[1], 72);
    assert.equal(worksheetRows(pricingFile.entries, 3).find(r => r[0] === '独立成品净重')[2], 74);
    assert.equal(worksheetRows(pricingFile.entries, 3).find(r => r[0] === '独立成品数量')[2], 74);
    assert.doesNotMatch(pricingFile.text, /单价|金额|成本|毛利|税率|税费|利润|自动报价/);
  });
  await api('finance', 'GET', `/pricing-files/${pricing.id}/download`, undefined, 'PRICING_FILE_NOT_AVAILABLE_FOR_FINANCE');
  const completedDetail = await api(engineer, 'GET', `/rnd-tasks/${task.id}/detail`);
  verify('complete trial/promotion/export workflow leaves experiment external-material records unchanged', () => {
    // This application exposes experiment-owned material records, not a material-master library API.
    assert.deepEqual(completedDetail.currentExperimentForm.materials, form.materials);
    assert.ok(!completedDetail.currentExperimentForm.materials.some(material => material.materialName === intermediateName));
  });
  handles.artifacts = arts; handles.submittedTrialVersion = submittedA.versionNo;
} finally {
  persist(); console.log(`RESULT ${results.filter(r => r.passed).length}/${results.length}; evidence=${output}`);
}
