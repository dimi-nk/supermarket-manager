import { useEffect, useState } from "react";
import { Link } from "react-router";
import { api, eur, fmtQty, today, type BasketLine, type Measure, type PipelineRequest, type PipelineResult } from "../api";
import { CandidatesView, ResolvedView, StagesView } from "../components/BasketViews";
import { Banner, NumberInput, Page, Tabs, useAction, useLoad } from "../components/ui";

interface Config {
  sourceType: "pantry" | "basket";
  profileId: string;
  at: string;
  candidates: string;
  ruleSetId: string;
  resolver: string;
  maxRetailers: number | null;
  retailer: string;
  export: boolean;
}

const DEFAULTS: Config = {
  sourceType: "pantry", profileId: "", at: "", candidates: "price:demo", ruleSetId: "", resolver: "optimizer",
  maxRetailers: null, retailer: "", export: true,
};

function loadConfig(): Config {
  try {
    return { ...DEFAULTS, ...JSON.parse(localStorage.getItem("sm.basket.config") ?? "{}") };
  } catch {
    return DEFAULTS;
  }
}

function loadLines(): BasketLine[] {
  try {
    return JSON.parse(localStorage.getItem("sm.basket.lines") ?? "[]");
  } catch {
    return [];
  }
}

let lineSeq = 0;
const newLine = (): BasketLine => ({ id: `line-${Date.now().toString(36)}-${lineSeq++}`, label: "", query: "", measure: "kg", quantity: 1 });

export default function BasketPage() {
  const [cfg, setCfg] = useState<Config>(loadConfig);
  const [lines, setLines] = useState<BasketLine[]>(loadLines);
  const [result, setResult] = useState<PipelineResult>();
  const [view, setView] = useState<"basket" | "candidates" | "lines">("basket");
  const stages = useLoad(() => api.pipeline.stages());
  const profiles = useLoad(() => api.pantry.profiles());
  const ruleSets = useLoad(() => api.rules.sets());
  const categories = useLoad(() => api.pantry.categories());
  const action = useAction();
  const apply = useAction();

  useEffect(() => {
    try {
      localStorage.setItem("sm.basket.config", JSON.stringify(cfg));
      localStorage.setItem("sm.basket.lines", JSON.stringify(lines));
    } catch {
      /* storage unavailable */
    }
  }, [cfg, lines]);

  const set = <K extends keyof Config>(k: K, v: Config[K]) => setCfg((c) => ({ ...c, [k]: v }));
  const setLine = (i: number, patch: Partial<BasketLine>) => setLines((ls) => ls.map((l, j) => (j === i ? { ...l, ...patch } : l)));

  const copyFromPantry = () =>
    action.run(async () => {
      const d = await api.pantry.delta(cfg.profileId || undefined, cfg.at || undefined);
      setLines(d.basket.lines);
      set("sourceType", "basket");
      return d;
    }, (d) => `Copied ${d.basket.lines.length} missing lines from the pantry; edit them freely.`);

  const run = () => {
    const req: PipelineRequest = {
      source: cfg.sourceType === "pantry"
        ? { type: "pantry", profileId: cfg.profileId || undefined, at: cfg.at || undefined }
        : { type: "basket", basket: { name: "Hand-made basket", lines: lines.filter((l) => l.quantity > 0).map(cleanLine) } },
      candidates: cfg.candidates || null,
      rules: cfg.ruleSetId ? { ruleSetId: cfg.ruleSetId } : null,
      resolve: cfg.resolver ? { resolver: cfg.resolver, maxRetailers: cfg.maxRetailers ?? undefined, retailer: cfg.retailer || undefined } : null,
      export: cfg.export,
    };
    action.run(() => api.pipeline.run(req)).then((r) => {
      if (!r) return;
      setResult(r);
      setView(r.resolved ? "basket" : r.candidates ? "candidates" : "lines");
    });
  };

  const sources = stages.data?.candidateSources ?? [];
  const resolvers = stages.data?.resolvers ?? [];
  const isPinned = cfg.candidates === "basket";

  return (
    <Page title="Basket builder" subtitle="Chain any of the modules. Every stage after the source is optional; the run stops at the last enabled stage.">
      <div className="stage-chain">
        <div className="stage">
          <h4><span><span className="stage-n">1</span>Source</span></h4>
          <select value={cfg.sourceType} onChange={(e) => set("sourceType", e.target.value as Config["sourceType"])}>
            <option value="pantry">Pantry: what's missing</option>
            <option value="basket">Hand-made basket</option>
          </select>
          {cfg.sourceType === "pantry" ? (
            <div className="stack" style={{ marginTop: "0.5rem" }}>
              <select value={cfg.profileId} onChange={(e) => set("profileId", e.target.value)}>
                <option value="">Active profile</option>
                {profiles.data?.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
              <label className="field">On date<input type="date" value={cfg.at} onChange={(e) => set("at", e.target.value)} placeholder={today()} /></label>
            </div>
          ) : <p className="small muted">{lines.length} line(s), edited below</p>}
        </div>
        <span className="arrow">→</span>
        <div className={`stage ${cfg.candidates ? "" : "off"}`}>
          <h4><span><span className="stage-n">2</span>Candidates</span></h4>
          <select value={cfg.candidates} onChange={(e) => set("candidates", e.target.value)}>
            <option value="">Off (stop at the basket)</option>
            {sources.map((s) => <option key={s.id} value={s.id}>{s.id}</option>)}
          </select>
          <p className="small muted">{sources.find((s) => s.id === cfg.candidates)?.description ?? "Find purchasable products for each line."}</p>
        </div>
        <span className="arrow">→</span>
        <div className={`stage ${cfg.ruleSetId ? "" : "off"}`}>
          <h4><span><span className="stage-n">3</span>Item rules</span></h4>
          <select value={cfg.ruleSetId} onChange={(e) => set("ruleSetId", e.target.value)}>
            <option value="">Off</option>
            {ruleSets.data?.map((r) => <option key={r.id} value={r.id}>{r.name} ({r.rules.length})</option>)}
          </select>
          <p className="small muted"><Link to="/rules">Edit rule sets</Link></p>
        </div>
        <span className="arrow">→</span>
        <div className={`stage ${cfg.resolver ? "" : "off"}`}>
          <h4><span><span className="stage-n">4</span>Final basket</span></h4>
          <select value={cfg.resolver} onChange={(e) => set("resolver", e.target.value)}>
            <option value="">Off (stop at candidates)</option>
            {resolvers.map((r) => <option key={r.id} value={r.id}>{r.id}</option>)}
          </select>
          {cfg.resolver && (
            <div className="row" style={{ marginTop: "0.5rem" }}>
              {cfg.resolver === "optimizer" && <label className="field">Max stores<NumberInput value={cfg.maxRetailers} onChange={(v) => set("maxRetailers", v)} step={1} min={1} placeholder="default" width="5.5rem" /></label>}
              <label className="field">Only retailer<input value={cfg.retailer} onChange={(e) => set("retailer", e.target.value)} placeholder="any" style={{ width: "6.5rem" }} /></label>
            </div>
          )}
          <label className="check small" style={{ marginTop: "0.5rem" }}><input type="checkbox" checked={cfg.export} onChange={(e) => set("export", e.target.checked)} /> checklist & store links</label>
        </div>
      </div>

      {cfg.sourceType === "basket" && (
        <div className="card" style={{ marginTop: "1rem" }}>
          <div className="row" style={{ justifyContent: "space-between" }}>
            <h3 style={{ margin: 0 }}>Hand-made lines</h3>
            <div className="row">
              <button onClick={copyFromPantry}>Copy from pantry delta</button>
              <button onClick={() => setLines((ls) => [...ls, newLine()])}>Add line</button>
              {lines.length > 0 && <button className="danger" onClick={() => setLines([])}>Clear</button>}
            </div>
          </div>
          <p className="small muted">
            A line is generic (query words must all appear in product names, e.g. <code>γιαούρτι</code>) or concrete (fill product/brand).
            {isPinned ? " With candidate source \"basket\", the price you enter is the only candidate." : " Prices are only used with candidate source \"basket\"."}
          </p>
          <LinesEditor lines={lines} setLine={setLine} remove={(i) => setLines((ls) => ls.filter((_, j) => j !== i))}
            categories={categories.data?.map((c) => ({ id: c.id, name: c.name })) ?? []} />
        </div>
      )}

      <div className="row" style={{ margin: "1rem 0" }}>
        <button className="primary" onClick={run} disabled={action.busy}>{action.busy ? "Running…" : "Run"}</button>
        {cfg.candidates.startsWith("price:posokanei") && <span className="small muted">PosoKanei is unofficial and may refuse requests (HTTP 403) from some networks.</span>}
      </div>
      <Banner kind="error">{action.error}</Banner>
      <Banner kind="ok">{action.message}</Banner>

      {result && (
        <div className="card">
          <div className="row" style={{ justifyContent: "space-between" }}>
            <h2 style={{ margin: 0 }}>Result {result.runId && <span className="badge">run {result.runId}</span>}</h2>
            {result.runId && result.resolved && (
              <button disabled={apply.busy || !!apply.message}
                onClick={() => apply.run(() => api.pipeline.apply(result.runId!, []), (r) =>
                  `Added ${r.applied.length} purchase(s) to stock${r.skipped.length ? `; skipped: ${r.skipped.join("; ")}` : ""}.`)}>
                I bought this → update stock
              </button>
            )}
          </div>
          <Banner kind="error">{apply.error}</Banner>
          <Banner kind="ok">{apply.message}</Banner>
          <StagesView result={result} />
          <Tabs value={view} onChange={setView} tabs={[
            ...(result.resolved ? [{ id: "basket" as const, label: "Final basket" }] : []),
            ...(result.candidates ? [{ id: "candidates" as const, label: "Candidates" }] : []),
            ...(result.basket ? [{ id: "lines" as const, label: "Basket lines" }] : []),
          ]} />
          {view === "basket" && result.resolved && <ResolvedView basket={result.resolved} checklist={result.checklist} />}
          {view === "candidates" && result.candidates && <CandidatesView set={result.candidates} />}
          {view === "lines" && result.basket && (
            <table>
              <thead><tr><th>Line</th><th>Query</th><th className="num">Quantity</th><th>Category</th></tr></thead>
              <tbody>{result.basket.lines.map((l) => (
                <tr key={l.id}><td>{l.label}<div className="small muted">{l.id}</div></td><td>{l.query}</td><td className="num">{fmtQty(l.quantity, l.measure)}</td><td>{l.categoryPath?.join(" ‹ ")}</td></tr>
              ))}</tbody>
            </table>
          )}
          {result.handoffs && result.handoffs.length > 0 && view === "basket" && (
            <p className="small muted">Store totals: {result.handoffs.map((h) => `${h.retailerName} ${eur(h.total)}`).join(" · ")}. Product links open the retailer's page so you can add items to your cart.</p>
          )}
        </div>
      )}
    </Page>
  );
}

function cleanLine(l: BasketLine): BasketLine {
  const product = l.product && (l.product.name || l.product.brand || l.product.barcode) ? l.product : null;
  const offer = l.offer && l.offer.price ? l.offer : null;
  return { ...l, label: l.label || l.query || l.id, product, offer, categoryId: l.categoryId || null };
}

function LinesEditor({ lines, setLine, remove, categories }: {
  lines: BasketLine[]; setLine: (i: number, p: Partial<BasketLine>) => void; remove: (i: number) => void; categories: { id: string; name: string }[];
}) {
  if (!lines.length) return <p className="muted">No lines yet.</p>;
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr><th>Label / query</th><th>Quantity</th><th>Category</th><th>Concrete product (optional)</th><th>Known price (optional)</th><th /></tr>
        </thead>
        <tbody>
          {lines.map((l, i) => (
            <tr key={l.id}>
              <td className="stack">
                <input placeholder="label" value={l.label ?? ""} onChange={(e) => setLine(i, { label: e.target.value })} />
                <input placeholder="search query" value={l.query ?? ""} onChange={(e) => setLine(i, { query: e.target.value })} />
              </td>
              <td>
                <div className="row">
                  <NumberInput value={l.quantity} onChange={(v) => setLine(i, { quantity: v ?? 0 })} width="5.5rem" />
                  <select value={l.measure} onChange={(e) => setLine(i, { measure: e.target.value as Measure })}>
                    <option value="kg">kg</option><option value="l">l</option><option value="each">pcs</option>
                  </select>
                </div>
              </td>
              <td>
                <select value={l.categoryId ?? ""} onChange={(e) => setLine(i, { categoryId: e.target.value || null, categoryPath: e.target.value ? [e.target.value] : [] })}>
                  <option value="">—</option>
                  {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </td>
              <td className="stack">
                <input placeholder="name" value={l.product?.name ?? ""} onChange={(e) => setLine(i, { product: { ...l.product, name: e.target.value } })} />
                <div className="row">
                  <input placeholder="brand" style={{ width: "7rem" }} value={l.product?.brand ?? ""} onChange={(e) => setLine(i, { product: { ...l.product, brand: e.target.value } })} />
                  <input placeholder="barcode" style={{ width: "8rem" }} value={l.product?.barcode ?? ""} onChange={(e) => setLine(i, { product: { ...l.product, barcode: e.target.value } })} />
                </div>
              </td>
              <td className="stack">
                <div className="row">
                  <input placeholder="retailer" style={{ width: "6rem" }} value={l.offer?.retailer ?? ""} onChange={(e) => setLine(i, { offer: { ...l.offer, retailer: e.target.value } })} />
                  <NumberInput placeholder="€/pack" value={l.offer?.price} onChange={(v) => setLine(i, { offer: { ...l.offer, price: v } })} step={0.01} width="5.5rem" />
                </div>
                <NumberInput placeholder="pack size" value={l.offer?.packSize} onChange={(v) => setLine(i, { offer: { ...l.offer, packSize: v } })} width="6rem" />
              </td>
              <td><button className="link danger" onClick={() => remove(i)} aria-label="Remove line">✕</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
