// Run with node tools/tests/page_top_inset_regression.mjs.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const source = fs.readFileSync(new URL('../../app/src/main/java/com/yuku/browser/core/PageTopInset.kt', import.meta.url), 'utf8');
const script = source.split('private val SCRIPT = """')[1].split('""".trimIndent()')[0];
// Check the complete injected script's syntax as well as the lifecycle below.
new vm.Script(script);
function fn(name) {
  const start = script.indexOf('          function ' + name + '(');
  const end = script.indexOf('\n          }', start) + '\n          }'.length;
  assert.ok(start >= 0 && end > start);
  return script.slice(start, end);
}
function element(position = 'fixed') {
  const values = new Map();
  return {
    position, isConnected: true,
    style: {
      getPropertyValue: p => values.get(p)?.value || '',
      getPropertyPriority: p => values.get(p)?.priority || '',
      setProperty: (p, value, priority = '') => values.set(p, { value, priority }),
      removeProperty: p => values.delete(p),
    },
    getAttribute: () => '',
  };
}
const ctx = vm.createContext({
  bar: 48, shifted: [], panelSizes: null,
  window: { innerWidth: 412, innerHeight: 900, getComputedStyle: el => ({ position: el.position }) },
  updateStartHold() {}, applyPad() {}, fullscreenElement: () => null,
  applyStartFill() {}, paintStrip() {}, docScrolls: () => true,
  writeFill() {}, candidates: () => [],
});
vm.runInContext(['write', 'restore', 'measure'].map(fn).join('\n'), ctx);
function hold(el, base = 0, inline = '') {
  const rec = { el, position: el.position, cls: '', levers: [{ p: 'top', base, inline, priority: '' }] };
  ctx.shifted = [rec];
  ctx.write(rec);
  return rec;
}
// A site-written offset must survive release, including a priority-only change.
for (const priority of ['', 'important']) {
  const el = element(); const rec = hold(el);
  el.style.setProperty('top', '12px', priority);
  ctx.restore(rec);
  assert.equal(el.style.getPropertyValue('top'), '12px');
  assert.equal(el.style.getPropertyPriority('top'), priority);
}
{
  const el = element(); const rec = hold(el);
  el.style.setProperty('top', '48px');
  ctx.restore(rec);
  assert.equal(el.style.getPropertyValue('top'), '48px');
}
// Repeated return-to-top cycles must release a fixed header's offset even
// when only an ancestor class changes its computed positioning mode.
for (let i = 0; i < 10; i++) {
  const el = element();
  const rec = hold(el);
  // This control was originally acquired at the document top, then became
  // fixed during scrolling. The absolute-control flag must not retain it.
  rec.absoluteControl = true;
  el.position = i % 2 ? 'sticky' : 'absolute';
  ctx.measure();
  assert.equal(ctx.shifted.length, 0);
  assert.equal(el.style.getPropertyValue('top'), '');
}
// Inline restyling without a class change must be re-candidated using the
// site's new baseline, and normal restoration retains the original value.
{
  const el = element(); hold(el);
  el.style.setProperty('top', '20px');
  ctx.measure();
  assert.equal(ctx.shifted.length, 0);
  assert.equal(el.style.getPropertyValue('top'), '20px');
  const rec = hold(el, 20, '20px');
  assert.equal(el.style.getPropertyValue('top'), '68px');
  ctx.restore(rec);
  assert.equal(el.style.getPropertyValue('top'), '20px');
}
console.log('PageTopInset regression checks passed');
