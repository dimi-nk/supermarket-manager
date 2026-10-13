import { Link, useNavigate, useParams } from "react-router";
import { api, eur } from "../api";
import { CandidatesView, ResolvedView, StagesView } from "../components/BasketViews";
import { Banner, Empty, Page, useAction, useLoad } from "../components/ui";

export default function RunsPage() {
  const { id } = useParams();
  const runs = useLoad(() => api.pipeline.runs());
  const run = useLoad(() => (id ? api.pipeline.get(id) : Promise.resolve(undefined)), [id]);
  const action = useAction();
  const nav = useNavigate();

  if (id) {
    const r = run.data;
    return (
      <Page title={`Basket ${id}`} subtitle={r ? new Date(r.createdAt).toLocaleString() : undefined}
        actions={<>
          <Link className="button" to="/runs">All baskets</Link>
          {r?.resolved && (
            <button className="primary" disabled={!!r.appliedAt || action.busy}
              onClick={() => action.run(() => api.pipeline.apply(id, []), (x) => `Added ${x.applied.length} purchase(s) to stock${x.skipped.length ? `; skipped: ${x.skipped.join("; ")}` : ""}.`).then(() => run.reload())}>
              {r.appliedAt ? "Recorded as bought" : "I bought this → update stock"}
            </button>
          )}
          <button className="danger" onClick={() => confirm("Delete this saved basket?") && action.run(() => api.pipeline.delete(id)).then(() => nav("/runs"))}>Delete</button>
        </>}>
        <Banner kind="error">{run.error ?? action.error}</Banner>
        <Banner kind="ok">{action.message}</Banner>
        {r && (
          <>
            <div className="card"><StagesView result={r} /></div>
            {r.resolved && <ResolvedView basket={r.resolved} checklist={r.checklist} />}
            {!r.resolved && r.candidates && <CandidatesView set={r.candidates} />}
          </>
        )}
      </Page>
    );
  }
  return (
    <Page title="Saved baskets" subtitle="Every run that produced a final basket is saved, so you can review it later and record it as bought once.">
      <div className="card">
        {!runs.data?.length ? <Empty>No saved baskets yet. Build one in the <Link to="/basket">basket builder</Link>.</Empty> : (
          <table>
            <thead><tr><th>When</th><th>Stages</th><th className="num">Total</th><th>Status</th></tr></thead>
            <tbody>{runs.data.map((r) => (
              <tr key={r.id}>
                <td className="nowrap"><Link to={`/runs/${r.id}`}>{new Date(r.createdAt).toLocaleString()}</Link></td>
                <td className="small muted">{r.summary}</td>
                <td className="num">{eur(r.total)}</td>
                <td>{r.appliedAt ? <span className="badge ok">bought</span> : <span className="badge">open</span>}</td>
              </tr>
            ))}</tbody>
          </table>
        )}
      </div>
    </Page>
  );
}
