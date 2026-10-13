import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { api, fmtQty, today, type CatalogProduct, type Category, type Measure, type Target, type TargetVersion } from "../api";
import { Banner, Empty, NumberInput, Page, Tabs, useAction, useLoad } from "../components/ui";

type Tab = "targets" | "stock" | "catalog" | "history";

export default function PantryPage() {
  const [params, setParams] = useSearchParams();
  const tab = (params.get("tab") as Tab) || "targets";
  const categories = useLoad(() => api.pantry.categories());
  const products = useLoad(() => api.pantry.products());
  const catalog = { categories: categories.data ?? [], products: products.data ?? [], reload: () => { categories.reload(); products.reload(); } };

  return (
    <Page title="Pantry" subtitle="Targets (what you want in the house, over time), stock (what you have) and the catalog they refer to.">
      <Tabs value={tab} onChange={(t) => setParams({ tab: t })} tabs={[
        { id: "targets", label: "Targets" }, { id: "stock", label: "Stock" }, { id: "catalog", label: "Catalog" }, { id: "history", label: "History" },
      ]} />
      {tab === "targets" && <TargetsTab {...catalog} />}
      {tab === "stock" && <StockTab {...catalog} />}
      {tab === "catalog" && <CatalogTab {...catalog} />}
      {tab === "history" && <HistoryTab {...catalog} />}
    </Page>
  );
}

interface CatalogProps { categories: Category[]; products: CatalogProduct[]; reload: () => void }

/** Categories in tree order with depth, for indented lists and selects. */
function useTree(categories: Category[]) {
  return useMemo(() => {
    const out: { c: Category; depth: number }[] = [];
    const walk = (parent: string | null, depth: number) => {
      categories.filter((c) => (c.parentId ?? null) === parent).sort((a, b) => a.name.localeCompare(b.name))
        .forEach((c) => { out.push({ c, depth }); walk(c.id, depth + 1); });
    };
    walk(null, 0);
    return out;
  }, [categories]);
}

function subjectOptions(_categories: Category[], products: CatalogProduct[], tree: { c: Category; depth: number }[]) {
  const opts: { key: string; label: string; measure: Measure }[] = [];
  for (const { c, depth } of tree) {
    opts.push({ key: `category:${c.id}`, label: `${"  ".repeat(depth)}${c.name}`, measure: c.measure });
    for (const p of products.filter((p) => p.categoryId === c.id)) {
      opts.push({ key: `product:${p.id}`, label: `${"  ".repeat(depth + 1)}• ${p.name}`, measure: c.measure });
    }
  }
  return opts;
}

function measureOf(subject: string, categories: Category[], products: CatalogProduct[]): Measure {
  const [type, id] = [subject.slice(0, subject.indexOf(":")), subject.slice(subject.indexOf(":") + 1)];
  const catId = type === "category" ? id : products.find((p) => p.id === id)?.categoryId;
  return categories.find((c) => c.id === catId)?.measure ?? "kg";
}

// ---------------- Targets ----------------

function TargetsTab({ categories, products }: CatalogProps) {
  const profiles = useLoad(() => api.pantry.profiles());
  const [profileId, setProfileId] = useState("");
  const pid = profileId || profiles.data?.find((p) => p.active)?.id || profiles.data?.[0]?.id || "";
  const versions = useLoad(() => (pid ? api.pantry.versions(pid) : Promise.resolve([] as TargetVersion[])), [pid]);
  const [versionId, setVersionId] = useState<number>();
  const tree = useTree(categories);
  const opts = subjectOptions(categories, products, tree);
  const action = useAction();
  const [newProfile, setNewProfile] = useState("");
  const [newFrom, setNewFrom] = useState(today());

  const current = useMemo(() => {
    const vs = versions.data ?? [];
    const inForce = [...vs].filter((v) => v.effectiveFrom <= today()).pop();
    return vs.find((v) => v.id === versionId) ?? inForce ?? vs[vs.length - 1];
  }, [versions.data, versionId]);
  const [draft, setDraft] = useState<Target[]>([]);
  const [note, setNote] = useState("");
  const [from, setFrom] = useState("");
  useEffect(() => {
    setDraft(current?.targets ?? []);
    setNote(current?.note ?? "");
    setFrom(current?.effectiveFrom ?? "");
  }, [current]);

  const save = () => current && action.run(() => api.pantry.updateVersion(current.id, { effectiveFrom: from, note, targets: draft }), "Targets saved")
    .then((v) => v && versions.reload());
  const setT = (i: number, p: Partial<Target>) => setDraft((d) => d.map((t, j) => (j === i ? { ...t, ...p } : t)));

  if (profiles.data && profiles.data.length === 0) {
    return (
      <div className="card">
        <h3>Create your first target pantry</h3>
        <div className="row">
          <input placeholder="e.g. Regular week" value={newProfile} onChange={(e) => setNewProfile(e.target.value)} />
          <button className="primary" disabled={!newProfile} onClick={() => action.run(async () => {
            const p = await api.pantry.saveProfile({ name: newProfile, active: true });
            await api.pantry.createVersion(p.id, { effectiveFrom: today(), targets: [] });
            return p;
          }).then(() => profiles.reload())}>Create</button>
        </div>
        <Banner kind="error">{action.error}</Banner>
      </div>
    );
  }

  return (
    <div className="side-grid">
      <div>
        <div className="card">
          <h3>Profiles</h3>
          <div className="stack">
            {profiles.data?.map((p) => (
              <div key={p.id} className="row" style={{ justifyContent: "space-between" }}>
                <button className={p.id === pid ? "primary" : ""} onClick={() => { setProfileId(p.id); setVersionId(undefined); }}>{p.name}</button>
                {p.active ? <span className="badge ok">active</span> : <button className="link small" onClick={() => action.run(() => api.pantry.activate(p.id)).then(() => profiles.reload())}>activate</button>}
              </div>
            ))}
            <div className="row">
              <input placeholder="New profile" value={newProfile} onChange={(e) => setNewProfile(e.target.value)} style={{ flex: 1 }} />
              <button disabled={!newProfile} onClick={() => action.run(() => api.pantry.saveProfile({ name: newProfile })).then((p) => {
                if (p) { setNewProfile(""); setProfileId(p.id); profiles.reload(); }
              })}>Add</button>
            </div>
          </div>
        </div>
        <div className="card">
          <h3>Versions over time</h3>
          <p className="small muted">Each version applies from its date until the next one, so you can plan a diet change ahead.</p>
          <div className="stack">
            {versions.data?.map((v) => (
              <button key={v.id} className={current?.id === v.id ? "primary" : ""} onClick={() => setVersionId(v.id)} style={{ justifyContent: "space-between" }}>
                <span>from {v.effectiveFrom}</span>
                <span className="small">{v.effectiveFrom > today() ? "planned" : v.id === [...(versions.data ?? [])].filter((x) => x.effectiveFrom <= today()).pop()?.id ? "in force" : "past"}</span>
              </button>
            ))}
            {pid && (
              <div className="row">
                <input type="date" value={newFrom} onChange={(e) => setNewFrom(e.target.value)} />
                <button onClick={() => action.run(() => api.pantry.createVersion(pid, { effectiveFrom: newFrom }), "New version created (copied from the targets in force on that date)")
                  .then((v) => { if (v) { versions.reload(); setVersionId(v.id); } })}>New version</button>
              </div>
            )}
          </div>
        </div>
      </div>
      <div className="card">
        {!current ? <Empty>No versions yet. Create one on the left.</Empty> : (
          <>
            <div className="row" style={{ justifyContent: "space-between" }}>
              <h3 style={{ margin: 0 }}>Targets</h3>
              <div className="row">
                <label className="field">Effective from<input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
                <label className="field">Note<input value={note} onChange={(e) => setNote(e.target.value)} /></label>
              </div>
            </div>
            <p className="small muted">
              Category targets are totals: products and sub-categories below count towards them. "4 × 0.5 kg yoghurt" plus "2 × Fage" means 2 kg of
              yoghurt of which 2 packs are Fage.
            </p>
            <div className="table-wrap">
              <table>
                <thead><tr><th>Item</th><th>Amount</th><th>or packs × size</th><th>Note</th><th /></tr></thead>
                <tbody>
                  {draft.map((t, i) => {
                    const m = measureOf(t.subject, categories, products);
                    return (
                      <tr key={i}>
                        <td>
                          <select value={t.subject} onChange={(e) => setT(i, { subject: e.target.value })}>
                            {opts.map((o) => <option key={o.key} value={o.key}>{o.label}</option>)}
                          </select>
                        </td>
                        <td><div className="row"><NumberInput value={t.amount} onChange={(v) => setT(i, { amount: v ?? 0 })} width="5.5rem" /> <span className="small muted">{m}</span></div></td>
                        <td><div className="row">
                          <NumberInput value={t.packCount} step={1} onChange={(v) => setT(i, { packCount: v, amount: v && t.packSize ? v * t.packSize : t.amount })} width="4.5rem" /> ×
                          <NumberInput value={t.packSize} onChange={(v) => setT(i, { packSize: v, amount: v && t.packCount ? v * t.packCount : t.amount })} width="5rem" />
                        </div></td>
                        <td><input value={t.note ?? ""} onChange={(e) => setT(i, { note: e.target.value })} /></td>
                        <td><button className="link danger" onClick={() => setDraft((d) => d.filter((_, j) => j !== i))} aria-label="Remove">✕</button></td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="row" style={{ marginTop: "0.75rem", justifyContent: "space-between" }}>
              <button disabled={!opts.length} onClick={() => setDraft((d) => [...d, { subject: opts.find((o) => !d.some((t) => t.subject === o.key))?.key ?? opts[0].key, amount: 1 }])}>Add target</button>
              <div className="row">
                <button className="danger" onClick={() => confirm("Delete this version?") && action.run(() => api.pantry.deleteVersion(current.id)).then(() => { setVersionId(undefined); versions.reload(); })}>Delete version</button>
                <button className="primary" onClick={save} disabled={action.busy}>Save</button>
              </div>
            </div>
            {!opts.length && <Banner kind="warn">Add categories in the Catalog tab first.</Banner>}
          </>
        )}
        <Banner kind="error">{action.error}</Banner>
        <Banner kind="ok">{action.message}</Banner>
      </div>
    </div>
  );
}

// ---------------- Stock ----------------

function StockTab({ categories, products }: CatalogProps) {
  const stock = useLoad(() => api.pantry.stock());
  const tree = useTree(categories);
  const opts = subjectOptions(categories, products, tree);
  const action = useAction();
  const [edits, setEdits] = useState<Record<string, number | null>>({});
  const [addSubject, setAddSubject] = useState("");
  const [addAmount, setAddAmount] = useState<number | null>(null);

  const setAmount = (subject: string, amount: number) =>
    action.run(() => api.pantry.setStock(subject, amount), "Stock updated").then(() => { setEdits((e) => ({ ...e, [subject]: null })); stock.reload(); });

  return (
    <div className="card">
      <h3>In the house now</h3>
      <p className="small muted">Record stock against a concrete product or a whole category ("about 300 g of some yoghurt"). Both count towards category targets.</p>
      {!stock.data?.length ? <Empty>Nothing recorded yet.</Empty> : (
        <table>
          <thead><tr><th>Item</th><th className="num">Amount</th><th>Set to</th><th>Updated</th></tr></thead>
          <tbody>
            {stock.data.map((s) => (
              <tr key={s.subject}>
                <td>{s.name} <span className="small muted">{s.subject.split(":")[0]}</span></td>
                <td className="num">{fmtQty(s.amount, s.measure)}</td>
                <td>
                  <div className="row">
                    <NumberInput value={edits[s.subject] ?? s.amount} onChange={(v) => setEdits((e) => ({ ...e, [s.subject]: v }))} width="5.5rem" />
                    <span className="small muted">{s.measure}</span>
                    {edits[s.subject] !== undefined && edits[s.subject] !== null && edits[s.subject] !== s.amount &&
                      <button onClick={() => setAmount(s.subject, edits[s.subject]!)}>Save</button>}
                    <button className="link small" onClick={() => setAmount(s.subject, 0)}>used up</button>
                  </div>
                </td>
                <td className="small muted">{s.updatedAt ? new Date(s.updatedAt).toLocaleDateString() : ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <h3>Record stock</h3>
      <div className="row">
        <select value={addSubject} onChange={(e) => setAddSubject(e.target.value)}>
          <option value="">Choose item…</option>
          {opts.map((o) => <option key={o.key} value={o.key}>{o.label}</option>)}
        </select>
        <NumberInput value={addAmount} onChange={setAddAmount} width="6rem" placeholder="amount" />
        <span className="small muted">{addSubject ? measureOf(addSubject, categories, products) : ""}</span>
        <button disabled={!addSubject || addAmount === null} onClick={() => setAmount(addSubject, addAmount!)}>Set</button>
      </div>
      <Banner kind="error">{action.error}</Banner>
      <Banner kind="ok">{action.message}</Banner>
    </div>
  );
}

// ---------------- Catalog ----------------

const emptyCategory: Partial<Category> = { name: "", measure: "kg", parentId: null, searchQuery: "" };
const emptyProduct: Partial<CatalogProduct> = { name: "", brand: "", categoryId: "", packSize: 0.5, barcode: "", searchQuery: "", privateLabel: false };

function CatalogTab({ categories, products, reload }: CatalogProps) {
  const tree = useTree(categories);
  const [cat, setCat] = useState<Partial<Category>>(emptyCategory);
  const [prod, setProd] = useState<Partial<CatalogProduct>>(emptyProduct);
  const action = useAction();
  const done = (r: unknown) => { if (r !== undefined) reload(); };

  return (
    <div className="grid2">
      <div className="card">
        <h3>Categories</h3>
        <p className="small muted">Generic kinds of things. Nest them (Dairy › Yoghurt › Strained); the search query is what price sources look for (Greek works best).</p>
        <table>
          <thead><tr><th>Name</th><th>Unit</th><th>Search query</th><th /></tr></thead>
          <tbody>
            {tree.map(({ c, depth }) => (
              <tr key={c.id}>
                <td><span className="tree-indent" style={{ width: `${depth * 1.1}rem` }} />{c.name}</td>
                <td>{c.measure}</td>
                <td className="small">{c.searchQuery}</td>
                <td className="nowrap">
                  <button className="link" onClick={() => setCat(c)}>edit</button>
                  <button className="link danger" onClick={() => confirm(`Delete ${c.name}?`) && action.run(() => api.pantry.deleteCategory(c.id)).then(() => reload())}>delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <h3>{cat.id ? `Edit ${cat.name}` : "New category"}</h3>
        <div className="row">
          <label className="field">Name<input value={cat.name ?? ""} onChange={(e) => setCat({ ...cat, name: e.target.value })} /></label>
          <label className="field">Unit<select value={cat.measure} onChange={(e) => setCat({ ...cat, measure: e.target.value as Measure })}>
            <option value="kg">kg</option><option value="l">l</option><option value="each">pieces</option></select></label>
          <label className="field">Parent<select value={cat.parentId ?? ""} onChange={(e) => setCat({ ...cat, parentId: e.target.value || null })}>
            <option value="">—</option>{tree.filter(({ c }) => c.id !== cat.id).map(({ c, depth }) => <option key={c.id} value={c.id}>{"  ".repeat(depth) + c.name}</option>)}</select></label>
          <label className="field">Search query<input value={cat.searchQuery ?? ""} onChange={(e) => setCat({ ...cat, searchQuery: e.target.value })} placeholder="γιαούρτι" /></label>
        </div>
        <div className="row" style={{ marginTop: "0.5rem" }}>
          <button className="primary" disabled={!cat.name} onClick={() => action.run(() => api.pantry.saveCategory(cat), "Category saved").then((r) => { done(r); if (r) setCat(emptyCategory); })}>Save category</button>
          {cat.id && <button onClick={() => setCat(emptyCategory)}>Cancel</button>}
        </div>
      </div>
      <div className="card">
        <h3>Products</h3>
        <p className="small muted">Concrete products, used for product-specific targets and stock. A barcode makes price matching exact.</p>
        <table>
          <thead><tr><th>Product</th><th>Category</th><th className="num">Pack</th><th /></tr></thead>
          <tbody>
            {products.map((p) => {
              const m = categories.find((c) => c.id === p.categoryId)?.measure ?? "kg";
              return (
                <tr key={p.id}>
                  <td>{p.name}<div className="small muted">{p.brand}{p.barcode && ` · ${p.barcode}`}</div></td>
                  <td>{categories.find((c) => c.id === p.categoryId)?.name}</td>
                  <td className="num">{fmtQty(p.packSize, m)}</td>
                  <td className="nowrap">
                    <button className="link" onClick={() => setProd(p)}>edit</button>
                    <button className="link danger" onClick={() => confirm(`Delete ${p.name}?`) && action.run(() => api.pantry.deleteProduct(p.id)).then(() => reload())}>delete</button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <h3>{prod.id ? `Edit ${prod.name}` : "New product"}</h3>
        <div className="row">
          <label className="field">Name<input value={prod.name ?? ""} onChange={(e) => setProd({ ...prod, name: e.target.value })} /></label>
          <label className="field">Brand<input value={prod.brand ?? ""} onChange={(e) => setProd({ ...prod, brand: e.target.value })} /></label>
          <label className="field">Category<select value={prod.categoryId ?? ""} onChange={(e) => setProd({ ...prod, categoryId: e.target.value })}>
            <option value="">Choose…</option>{tree.map(({ c, depth }) => <option key={c.id} value={c.id}>{"  ".repeat(depth) + c.name}</option>)}</select></label>
          <label className="field">Pack size ({categories.find((c) => c.id === prod.categoryId)?.measure ?? "unit"})<NumberInput value={prod.packSize} onChange={(v) => setProd({ ...prod, packSize: v ?? 0 })} width="6rem" /></label>
          <label className="field">Barcode<input value={prod.barcode ?? ""} onChange={(e) => setProd({ ...prod, barcode: e.target.value })} /></label>
          <label className="field">Search query<input value={prod.searchQuery ?? ""} onChange={(e) => setProd({ ...prod, searchQuery: e.target.value })} /></label>
        </div>
        <div className="row" style={{ marginTop: "0.5rem" }}>
          <button className="primary" disabled={!prod.name || !prod.categoryId} onClick={() => action.run(() => api.pantry.saveProduct(prod), "Product saved").then((r) => { done(r); if (r) setProd(emptyProduct); })}>Save product</button>
          {prod.id && <button onClick={() => setProd(emptyProduct)}>Cancel</button>}
        </div>
      </div>
      <div style={{ gridColumn: "1 / -1" }}>
        <Banner kind="error">{action.error}</Banner>
        <Banner kind="ok">{action.message}</Banner>
      </div>
    </div>
  );
}

// ---------------- History ----------------

function HistoryTab({ categories, products }: CatalogProps) {
  const events = useLoad(() => api.pantry.history());
  const name = (s: string) => {
    const id = s.slice(s.indexOf(":") + 1);
    return s.startsWith("product:") ? products.find((p) => p.id === id)?.name ?? id : categories.find((c) => c.id === id)?.name ?? id;
  };
  return (
    <div className="card">
      {!events.data?.length ? <Empty>No stock changes yet.</Empty> : (
        <table>
          <thead><tr><th>When</th><th>What</th><th>Item</th><th className="num">Change</th><th className="num">Now</th><th>Details</th></tr></thead>
          <tbody>
            {events.data.map((e) => {
              const m = measureOf(e.subject, categories, products);
              return (
                <tr key={e.id}>
                  <td className="small nowrap">{new Date(e.at).toLocaleString()}</td>
                  <td><span className={`badge ${e.kind === "purchase" ? "ok" : ""}`}>{e.kind}</span></td>
                  <td>{name(e.subject)}</td>
                  <td className="num">{e.delta > 0 ? "+" : ""}{fmtQty(e.delta, m)}</td>
                  <td className="num">{fmtQty(e.resulting, m)}</td>
                  <td className="small muted">{[e.retailer, e.price !== undefined && e.price !== null ? `€${e.price.toFixed(2)}` : null, e.note].filter(Boolean).join(" · ")}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}
