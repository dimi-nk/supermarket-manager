import { useState } from "react";
import { api, type Product } from "../api";
import { Banner, Page, useAction, useLoad } from "../components/ui";
import { ProductTable } from "./Prices";

export default function SupermarketsPage() {
  const adapters = useLoad(() => api.supermarkets.list());
  const [adapter, setAdapter] = useState("ab");
  const [q, setQ] = useState("");
  const [results, setResults] = useState<Product[]>();
  const search = useAction();
  const go = () => q && search.run(() => api.supermarkets.search(adapter, q)).then(setResults);

  return (
    <Page title="Supermarkets" subtitle="Direct, read-only connections to retailer e-shops: live products and prices from one store, no price-comparison service needed.">
      <div className="card">
        <table>
          <thead><tr><th>Retailer</th><th>Capabilities</th><th>Candidate source id</th></tr></thead>
          <tbody>{adapters.data?.map((a) => (
            <tr key={a.id}><td>{a.displayName}</td><td>{a.capabilities.map((c) => <span key={c} className="badge" style={{ marginRight: 4 }}>{c.toLowerCase().replace("_", " ")}</span>)}</td><td><code>{a.candidateSource}</code></td></tr>
          ))}</tbody>
        </table>
        <p className="small muted">
          Filling a retailer cart automatically needs your logged-in session and a captured trace of the retailer's site; it is not built yet.
          Until then, every final basket comes with product links for adding items by hand.
        </p>
      </div>
      <div className="card">
        <h3>Search a store</h3>
        <div className="row">
          <select value={adapter} onChange={(e) => setAdapter(e.target.value)}>{adapters.data?.map((a) => <option key={a.id} value={a.id}>{a.displayName}</option>)}</select>
          <input value={q} onChange={(e) => setQ(e.target.value)} onKeyDown={(e) => e.key === "Enter" && go()} placeholder="e.g. φέτα" style={{ flex: 1 }} />
          <button className="primary" onClick={go} disabled={!q || search.busy}>Search</button>
        </div>
        <Banner kind="error">{search.error}</Banner>
        {results && <ProductTable products={results} />}
      </div>
    </Page>
  );
}
