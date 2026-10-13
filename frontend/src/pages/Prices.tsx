import { useState } from "react";
import { api, eur, type PriceSettings, type Product, type RetailerSetting } from "../api";
import { Banner, Empty, NumberInput, Page, useAction, useLoad } from "../components/ui";

export function ProductTable({ products }: { products: Product[] }) {
  if (!products.length) return <Empty>No products found.</Empty>;
  return (
    <div className="table-wrap">
      <table>
        <thead><tr><th>Product</th><th>Pack</th><th>Offers</th></tr></thead>
        <tbody>
          {products.map((p) => (
            <tr key={p.source + p.id}>
              <td>{p.name}<div className="small muted">{p.brand}{p.privateLabel && " · own brand"}{p.barcode && ` · ${p.barcode}`}</div></td>
              <td className="small nowrap">{p.unitQuantity ? `${p.unitQuantity} ${p.unit}` : "—"}</td>
              <td className="small">
                {[...p.offers].sort((a, b) => a.price - b.price).map((o) => (
                  <div key={o.retailer} className="nowrap">
                    {o.url ? <a href={o.url} target="_blank" rel="noreferrer">{o.retailerName}</a> : o.retailerName} {eur(o.price)}
                    {o.discount && <> <span className="badge ok">offer</span></>}
                  </div>
                ))}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function PricesPage() {
  const providers = useLoad(() => api.prices.providers());
  const settings = useLoad(() => api.prices.settings());
  const retailers = useLoad(() => api.prices.retailers());
  const [provider, setProvider] = useState("demo");
  const [q, setQ] = useState("");
  const [results, setResults] = useState<Product[]>();
  const search = useAction();
  const save = useAction();
  const [s, setS] = useState<PriceSettings>();
  const cur = s ?? settings.data;
  const [r, setR] = useState<RetailerSetting>({ retailer: "", fee: 0 });

  return (
    <Page title="Price comparison" subtitle="Multi-retailer price sources and the optimizer that chooses the cheapest set of stores.">
      <div className="card">
        <h3>Search prices</h3>
        <div className="row">
          <select value={provider} onChange={(e) => setProvider(e.target.value)}>
            {providers.data?.map((p) => <option key={p.id} value={p.id}>{p.id}</option>)}
          </select>
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="e.g. γιαούρτι στραγγιστό" style={{ flex: 1 }}
            onKeyDown={(e) => e.key === "Enter" && q && search.run(() => api.prices.search(provider, q)).then(setResults)} />
          <button className="primary" disabled={!q || search.busy} onClick={() => search.run(() => api.prices.search(provider, q)).then(setResults)}>Search</button>
        </div>
        <p className="small muted">{providers.data?.find((p) => p.id === provider)?.description}</p>
        <Banner kind="error">{search.error}</Banner>
        {results && <ProductTable products={results} />}
      </div>
      <div className="grid2">
        <div className="card">
          <h3>Optimizer defaults</h3>
          {cur && (
            <div className="stack">
              <label className="field">Max stores per basket<NumberInput value={cur.maxRetailers} step={1} min={1} onChange={(v) => setS({ ...cur, maxRetailers: v ?? 1 })} /></label>
              <label className="field">Pack rounding tolerance (0.1 = needing 1.08 packs buys 1)<NumberInput value={cur.packRoundingTolerance} step={0.05} onChange={(v) => setS({ ...cur, packRoundingTolerance: v ?? 0 })} /></label>
              <label className="field">Warn when prices are older than (days)<NumberInput value={cur.maxPriceAgeDays} step={1} onChange={(v) => setS({ ...cur, maxPriceAgeDays: v ?? 7 })} /></label>
              <label className="field">PosoKanei result pages per search (50 each)<NumberInput value={cur.searchPages} step={1} min={1} onChange={(v) => setS({ ...cur, searchPages: v ?? 1 })} /></label>
              <div className="row">
                <button className="primary" onClick={() => save.run(() => api.prices.saveSettings(cur), "Saved").then(() => settings.reload())}>Save</button>
                <button onClick={() => save.run(() => api.prices.clearCache(), (x) => `Cleared ${x.deleted} cached searches`)}>Clear price cache</button>
              </div>
            </div>
          )}
          <Banner kind="error">{save.error}</Banner>
          <Banner kind="ok">{save.message}</Banner>
        </div>
        <div className="card">
          <h3>Retailer fees</h3>
          <p className="small muted">Delivery fee, free-delivery threshold and minimum basket per retailer id (as the price source reports it).</p>
          {retailers.data?.length ? (
            <table>
              <thead><tr><th>Retailer</th><th className="num">Fee</th><th className="num">Free above</th><th className="num">Min basket</th><th /></tr></thead>
              <tbody>{retailers.data.map((x) => (
                <tr key={x.retailer}>
                  <td>{x.displayName || x.retailer}</td><td className="num">{eur(x.fee)}</td><td className="num">{eur(x.freeAbove)}</td><td className="num">{eur(x.minBasket)}</td>
                  <td className="nowrap"><button className="link" onClick={() => setR(x)}>edit</button><button className="link danger" onClick={() => save.run(() => api.prices.deleteRetailer(x.retailer)).then(() => retailers.reload())}>delete</button></td>
                </tr>
              ))}</tbody>
            </table>
          ) : <p className="muted small">No fees configured: every store is assumed free.</p>}
          <div className="row" style={{ marginTop: "0.5rem" }}>
            <label className="field">Retailer id<input value={r.retailer} onChange={(e) => setR({ ...r, retailer: e.target.value })} style={{ width: "7rem" }} placeholder="ab" /></label>
            <label className="field">Fee<NumberInput value={r.fee} step={0.5} onChange={(v) => setR({ ...r, fee: v ?? 0 })} width="5rem" /></label>
            <label className="field">Free above<NumberInput value={r.freeAbove} step={1} onChange={(v) => setR({ ...r, freeAbove: v })} width="5rem" /></label>
            <label className="field">Min basket<NumberInput value={r.minBasket} step={1} onChange={(v) => setR({ ...r, minBasket: v })} width="5rem" /></label>
            <button disabled={!r.retailer} onClick={() => save.run(() => api.prices.saveRetailer(r), "Retailer saved").then(() => { setR({ retailer: "", fee: 0 }); retailers.reload(); })}>Save</button>
          </div>
        </div>
      </div>
    </Page>
  );
}
