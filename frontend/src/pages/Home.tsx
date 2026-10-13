import { Link, useNavigate } from "react-router";
import { api, eur, fmtQty } from "../api";
import { Banner, Empty, Page, useAction, useLoad } from "../components/ui";

export default function Home() {
  const profiles = useLoad(() => api.pantry.profiles());
  const delta = useLoad(() => api.pantry.delta().catch(() => undefined), [profiles.data?.length]);
  const runs = useLoad(() => api.pipeline.runs());
  const action = useAction();
  const nav = useNavigate();

  if (profiles.loading) return null;
  if (profiles.data && profiles.data.length === 0) {
    return (
      <Page title="Welcome" subtitle="Plan your weekly shop from a target pantry, your rules and real supermarket prices.">
        <div className="card">
          <h2>Get started</h2>
          <p>Set up categories (e.g. <em>yoghurt</em>), optionally concrete products (<em>Fage 2% 2×200g</em>), and a target pantry.
            The app works out what is missing and builds the cheapest basket.</p>
          <div className="row" style={{ marginTop: "0.75rem" }}>
            <button className="primary" disabled={action.busy}
              onClick={() => action.run(() => api.pantry.loadExamples(), (r) => r.message).then(() => profiles.reload())}>
              Load example pantry
            </button>
            <Link className="button" to="/pantry">Start from scratch</Link>
            <Link className="button" to="/basket">Build a basket by hand</Link>
          </div>
          <Banner kind="error">{action.error}</Banner>
        </div>
      </Page>
    );
  }

  const d = delta.data;
  const open = d?.lines.filter((l) => l.need > 1e-9) ?? [];
  return (
    <Page title="This week" subtitle={d ? <>Profile <strong>{profiles.data?.find((p) => p.id === d.profileId)?.name ?? d.profileId}</strong>, targets from {d.effectiveFrom}</> : undefined}
      actions={<>
        <Link className="button" to="/pantry?tab=stock">Update stock</Link>
        <button className="primary" onClick={() => nav("/basket")} disabled={!open.length}>Build basket</button>
      </>}>
      {!d && <Banner kind="warn">No targets in force for the active profile. Add some under <Link to="/pantry?tab=targets">Pantry → Targets</Link>.</Banner>}
      {d && (
        <div className="card">
          <h2>Missing to reach your targets</h2>
          {d.lines.length === 0 ? <Empty>No targets yet.</Empty> : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Item</th><th className="num">Target</th><th className="num">In stock</th><th className="num">Covered below</th><th className="num">Missing</th></tr></thead>
                <tbody>
                  {d.lines.map((l) => (
                    <tr key={l.subject}>
                      <td>{l.name} <span className="small muted">{l.subject.startsWith("product:") ? "product" : "category"}</span></td>
                      <td className="num">{fmtQty(l.target, l.measure)}</td>
                      <td className="num">{fmtQty(l.have, l.measure)}</td>
                      <td className="num muted">{l.fromTargetsBelow > 0 ? fmtQty(l.fromTargetsBelow, l.measure) : ""}</td>
                      <td className="num">{l.need > 0 ? <strong>{fmtQty(l.need, l.measure)}{l.suggestedPacks ? ` (${l.suggestedPacks} × ${fmtQty(l.packSize!, l.measure)})` : ""}</strong> : <span className="badge ok">stocked</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="small muted">"Covered below" is what more specific targets (a product, a sub-category) already plan to buy towards this one.</p>
        </div>
      )}
      <div className="card">
        <h2>Recent baskets</h2>
        {!runs.data?.length ? <Empty>No saved baskets yet.</Empty> : (
          <table>
            <tbody>
              {runs.data.slice(0, 5).map((r) => (
                <tr key={r.id}>
                  <td className="nowrap"><Link to={`/runs/${r.id}`}>{new Date(r.createdAt).toLocaleString()}</Link></td>
                  <td className="small muted">{r.summary}</td>
                  <td className="num">{eur(r.total)}</td>
                  <td>{r.appliedAt ? <span className="badge ok">bought</span> : <span className="badge">open</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </Page>
  );
}
