import { useEffect, useState } from "react";
import { api, type MixPolicy, type Rule, type RuleSet, type RuleType } from "../api";
import { Banner, Empty, NumberInput, Page, useAction, useLoad } from "../components/ui";

const TYPES: { type: RuleType; label: string; help: string; kind: "values" | "range" | "flag" | "mix" }[] = [
  { type: "DENY", label: "Deny products", help: "Brand or name words, gtin:<barcode> or id:<productId>. Deny beats allow.", kind: "values" },
  { type: "ALLOW", label: "Allow only", help: "Only products matching one of these may be bought.", kind: "values" },
  { type: "UNIT_PRICE", label: "Unit price range", help: "EUR per kg / l / piece, e.g. never above €5/kg of yoghurt.", kind: "range" },
  { type: "PACK_PRICE", label: "Pack price range", help: "EUR per pack.", kind: "range" },
  { type: "PACK_SIZE", label: "Pack size range", help: "In the line's unit (kg, l or pieces).", kind: "range" },
  { type: "NO_PRIVATE_LABEL", label: "No own-brand", help: "Drop retailer private-label products.", kind: "flag" },
  { type: "RETAILER_DENY", label: "Exclude retailers", help: "Retailer ids or names, e.g. ab, lidl.", kind: "values" },
  { type: "RETAILER_ALLOW", label: "Only these retailers", help: "Retailer ids or names.", kind: "values" },
  { type: "ONLY_DISCOUNTED", label: "Only offers", help: "Keep only discounted offers.", kind: "flag" },
  { type: "MIX_POLICY", label: "Mixing", help: "One product per line (default), one brand with mixed pack sizes, or any cheapest mix.", kind: "mix" },
];

const blank = (): RuleSet => ({ name: "", description: "", rules: [] });

export default function RulesPage() {
  const sets = useLoad(() => api.rules.sets());
  const categories = useLoad(() => api.pantry.categories());
  const [selected, setSelected] = useState<string>();
  const [draft, setDraft] = useState<RuleSet>(blank());
  const action = useAction();

  useEffect(() => {
    const s = sets.data?.find((x) => x.id === selected) ?? (selected === undefined ? sets.data?.[0] : undefined);
    setDraft(s ? structuredClone(s) : blank());
  }, [selected, sets.data]);

  const setRule = (i: number, p: Partial<Rule>) => setDraft((d) => ({ ...d, rules: d.rules.map((r, j) => (j === i ? { ...r, ...p } : r)) }));

  return (
    <Page title="Item rules" subtitle="Control which products may be picked. Rules filter any candidate set, from any source, and can be scoped to a category.">
      <div className="side-grid">
        <div className="card">
          <h3>Rule sets</h3>
          <div className="stack">
            {sets.data?.map((s) => (
              <button key={s.id} className={draft.id === s.id ? "primary" : ""} onClick={() => setSelected(s.id)}>{s.name} <span className="small">({s.rules.length})</span></button>
            ))}
            <button onClick={() => { setSelected(""); setDraft(blank()); }}>+ New rule set</button>
          </div>
        </div>
        <div className="card">
          <div className="row">
            <label className="field" style={{ flex: 1 }}>Name<input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="e.g. Weekly defaults" /></label>
            <label className="field" style={{ flex: 2 }}>Description<input value={draft.description ?? ""} onChange={(e) => setDraft({ ...draft, description: e.target.value })} /></label>
          </div>
          {draft.rules.length === 0 && <Empty>No rules yet.</Empty>}
          {draft.rules.map((r, i) => {
            const t = TYPES.find((x) => x.type === r.type)!;
            return (
              <div key={i} className="card" style={{ background: "var(--surface-2)", marginTop: "0.75rem" }}>
                <div className="row" style={{ justifyContent: "space-between" }}>
                  <div className="row">
                    <select value={r.type} onChange={(e) => setRule(i, { type: e.target.value as RuleType, values: [], min: null, max: null, mix: e.target.value === "MIX_POLICY" ? "SINGLE_BRAND" : null })}>
                      {TYPES.map((x) => <option key={x.type} value={x.type}>{x.label}</option>)}
                    </select>
                    <span className="small muted">for</span>
                    <select value={r.scope?.categoryId ?? ""} onChange={(e) => setRule(i, { scope: { ...r.scope, categoryId: e.target.value || null } })}>
                      <option value="">all lines</option>
                      {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name} (and below)</option>)}
                    </select>
                  </div>
                  <div className="row">
                    <label className="check small"><input type="checkbox" checked={r.enabled !== false} onChange={(e) => setRule(i, { enabled: e.target.checked })} /> on</label>
                    <button className="link danger" onClick={() => setDraft((d) => ({ ...d, rules: d.rules.filter((_, j) => j !== i) }))} aria-label="Remove rule">✕</button>
                  </div>
                </div>
                <p className="small muted">{t.help}</p>
                {t.kind === "values" && (
                  <input style={{ width: "100%" }} placeholder="comma-separated, e.g. ΔΕΛΤΑ, ΟΛΥΜΠΟΣ, gtin:5201234567890"
                    value={(r.values ?? []).join(", ")} onChange={(e) => setRule(i, { values: e.target.value.split(",").map((v) => v.trim()).filter(Boolean) })} />
                )}
                {t.kind === "range" && (
                  <div className="row">
                    <label className="field">Min<NumberInput value={r.min} onChange={(v) => setRule(i, { min: v })} step={0.01} /></label>
                    <label className="field">Max<NumberInput value={r.max} onChange={(v) => setRule(i, { max: v })} step={0.01} /></label>
                  </div>
                )}
                {t.kind === "mix" && (
                  <select value={r.mix ?? "SINGLE_BRAND"} onChange={(e) => setRule(i, { mix: e.target.value as MixPolicy })}>
                    <option value="SINGLE_PRODUCT">One product per line</option>
                    <option value="SINGLE_BRAND">One brand, any pack sizes</option>
                    <option value="ANY">Any mix (cheapest)</option>
                  </select>
                )}
                <input style={{ width: "100%", marginTop: "0.4rem" }} placeholder="note (optional)" value={r.note ?? ""} onChange={(e) => setRule(i, { note: e.target.value })} />
              </div>
            );
          })}
          <div className="row" style={{ justifyContent: "space-between", marginTop: "0.75rem" }}>
            <button onClick={() => setDraft((d) => ({ ...d, rules: [...d.rules, { type: "DENY", values: [], enabled: true }] }))}>Add rule</button>
            <div className="row">
              {draft.id && <button className="danger" onClick={() => confirm("Delete this rule set?") && action.run(() => api.rules.delete(draft.id!)).then(() => { setSelected(undefined); sets.reload(); })}>Delete</button>}
              <button className="primary" disabled={!draft.name || action.busy} onClick={() => action.run(() => api.rules.save(draft), "Rule set saved").then((s) => { if (s) { setSelected(s.id); sets.reload(); } })}>Save</button>
            </div>
          </div>
          <Banner kind="error">{action.error}</Banner>
          <Banner kind="ok">{action.message}</Banner>
          <p className="small muted" style={{ marginTop: "1rem" }}>
            Matching ignores case and Greek accents; each word must start a word in the brand or name ("βιο" matches "βιολογικό").
            All applicable rules apply together; for mixing, the most specific scope wins.
          </p>
        </div>
      </div>
    </Page>
  );
}
