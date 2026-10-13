import { useState } from "react";
import { api, eur, fmtQty, type CandidateSet, type PipelineResult, type ResolvedBasket } from "../api";
import { Banner } from "./ui";

const REASONS: Record<string, string> = {
  queryMismatch: "not matching the query", notRequestedProduct: "not the requested product", unknownPack: "unreadable pack size",
  wrongMeasure: "different unit", otherPackSize: "other pack size", invalidPrice: "no valid price", denied: "denied",
  notAllowed: "not on allow list", privateLabel: "private label", unitPrice: "unit price out of range", packPrice: "pack price out of range",
  packSize: "pack size out of range", retailer: "excluded retailer", onlyDiscounted: "not discounted",
};

function download(name: string, text: string, type: string) {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const a = Object.assign(document.createElement("a"), { href: url, download: name });
  a.click();
  URL.revokeObjectURL(url);
}

export function ResolvedView({ basket, checklist }: { basket: ResolvedBasket; checklist?: string }) {
  const [error, setError] = useState<string>();
  const csv = async () => {
    try {
      download("shopping-list.csv", await api.supermarkets.exportCsv(basket), "text/csv");
    } catch (e) {
      setError((e as Error).message);
    }
  };
  return (
    <div>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <span className="total">{basket.total !== undefined && basket.total !== null ? eur(basket.total) : "Unpriced"}</span>{" "}
          <span className="muted small">via {basket.resolver} · {basket.stores.length} store(s)</span>
        </div>
        <div className="row">
          {checklist && <button onClick={() => navigator.clipboard.writeText(checklist)}>Copy checklist</button>}
          {checklist && <button onClick={() => download("shopping-list.md", checklist, "text/markdown")}>Download .md</button>}
          <button onClick={csv}>Download .csv</button>
        </div>
      </div>
      <Banner kind="error">{error}</Banner>
      {basket.alternatives.length > 1 && (
        <p className="small muted">
          Store-count trade-off:{" "}
          {basket.alternatives.map((a) => `≤${a.maxRetailers} store${a.maxRetailers > 1 ? "s" : ""}: ${eur(a.total)} (${a.retailers.join(" + ")})`).join(" · ")}
        </p>
      )}
      {basket.warnings.map((w, i) => <Banner key={i} kind="warn">{w}</Banner>)}
      {basket.stores.map((s) => (
        <div key={s.retailer} className="card store">
          <div className="row" style={{ justifyContent: "space-between" }}>
            <h3 style={{ margin: 0 }}>{s.retailerName} {s.belowMinimum && <span className="badge bad">below minimum basket</span>}</h3>
            <div className="small">
              {eur(s.subtotal)}{s.fee > 0 && <> + {eur(s.fee)} fee</>} = <strong>{eur(s.total)}</strong>
            </div>
          </div>
          <div className="table-wrap">
            <table>
              <thead><tr><th>For</th><th>Buy</th><th className="num">Packs</th><th className="num">Unit price</th><th className="num">Cost</th></tr></thead>
              <tbody>
                {s.lines.flatMap((l) => l.parts.map((p, i) => (
                  <tr key={l.lineId + i}>
                    <td>{i === 0 && <>{l.label}<div className="small muted">need {fmtQty(l.need, l.measure)} → {fmtQty(l.amount, l.measure)}</div></>}</td>
                    <td>
                      {p.candidate.url ? <a href={p.candidate.url} target="_blank" rel="noreferrer">{p.candidate.name}</a> : p.candidate.name}
                      <div className="small muted">
                        {p.candidate.brand} · {fmtQty(p.candidate.packSize, p.candidate.measure)}
                        {p.candidate.discount && <> · <span className="badge ok">offer</span></>}
                        {p.candidate.unitPriceMismatch && <> · <span className="badge warn">check pack size</span></>}
                      </div>
                    </td>
                    <td className="num">{p.packs}</td>
                    <td className="num">{p.candidate.unitPrice ? `${eur(p.candidate.unitPrice)}/${p.candidate.measure === "each" ? "pc" : p.candidate.measure}` : "—"}</td>
                    <td className="num">{eur(p.cost)}</td>
                  </tr>
                )))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
      {basket.unresolved.length > 0 && (
        <div className="card">
          <h3>Not planned</h3>
          <ul>
            {basket.unresolved.map((u) => (
              <li key={u.lineId}><strong>{u.label}</strong> <span className={`badge ${u.status === "LEFT_OUT" ? "warn" : "bad"}`}>{u.status.toLowerCase().replace("_", " ")}</span><div className="small muted">{u.reason}</div></li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export function CandidatesView({ set }: { set: CandidateSet }) {
  return (
    <div>
      {set.warnings.map((w, i) => <Banner key={i} kind="warn">{w}</Banner>)}
      {set.lines.map((lc) => {
        const rejected = Object.entries(lc.diagnostics.rejected);
        const sorted = [...lc.candidates].sort((a, b) => (a.unitPrice ?? 1e9) - (b.unitPrice ?? 1e9));
        return (
          <details key={lc.line.id} className="card">
            <summary>
              <strong>{lc.line.label}</strong> <span className="muted small">({fmtQty(lc.line.quantity, lc.line.measure)})</span>{" "}
              <span className={`badge ${lc.candidates.length ? "ok" : "bad"}`}>{lc.candidates.length} candidates</span>{" "}
              {lc.mixPolicy !== "SINGLE_PRODUCT" && <span className="badge">{lc.mixPolicy.toLowerCase().replace("_", " ")}</span>}
            </summary>
            <p className="small muted">
              Searched {lc.diagnostics.searched}
              {rejected.length > 0 && <> · dropped: {rejected.map(([k, v]) => `${v} ${REASONS[k] ?? k}`).join(", ")}</>}
              {lc.diagnostics.closestRejected && <> · closest rejected: {lc.diagnostics.closestRejected.name} at {lc.diagnostics.closestRejected.retailerName}, {eur(lc.diagnostics.closestRejected.unitPrice)}/{lc.line.measure}</>}
              {lc.diagnostics.notes.map((n) => ` · ${n}`)}
            </p>
            {sorted.length > 0 && (
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Product</th><th>Retailer</th><th className="num">Pack</th><th className="num">Price</th><th className="num">Unit price</th></tr></thead>
                  <tbody>
                    {sorted.slice(0, 40).map((c, i) => (
                      <tr key={i}>
                        <td>{c.url ? <a href={c.url} target="_blank" rel="noreferrer">{c.name}</a> : c.name}<div className="small muted">{c.brand}{c.privateLabel && " · own brand"}{c.barcode && ` · ${c.barcode}`}</div></td>
                        <td>{c.retailerName}{c.discount && <> <span className="badge ok">offer</span></>}</td>
                        <td className="num">{fmtQty(c.packSize, c.measure)}</td>
                        <td className="num">{eur(c.price)}</td>
                        <td className="num">{c.unitPrice ? eur(c.unitPrice) : "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {sorted.length > 40 && <p className="small muted">…and {sorted.length - 40} more</p>}
              </div>
            )}
          </details>
        );
      })}
    </div>
  );
}

export function StagesView({ result }: { result: PipelineResult }) {
  return (
    <ol className="small">
      {result.stages.map((s) => <li key={s.name}><strong>{s.name}</strong>: {s.detail} <span className="muted">({s.millis} ms)</span></li>)}
    </ol>
  );
}
