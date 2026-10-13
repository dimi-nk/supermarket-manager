import { NavLink, Route, Routes } from "react-router";
import { api } from "./api";
import Home from "./pages/Home";
import PantryPage from "./pages/Pantry";
import RulesPage from "./pages/Rules";
import PricesPage from "./pages/Prices";
import SupermarketsPage from "./pages/Supermarkets";
import BasketPage from "./pages/Basket";
import RunsPage from "./pages/Runs";
import AgentsPage from "./pages/Agents";

export default function App() {
  const quit = async () => {
    if (!confirm("Stop Supermarket Manager? The page will stop working until you start the app again.")) return;
    await api.system.shutdown();
    document.body.innerHTML = "<p style='padding:2rem;font-family:sans-serif'>Supermarket Manager has stopped. You can close this tab.</p>";
  };
  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand"><img src="/basket.svg" alt="" /> Supermarket Manager</div>
        <nav className="nav">
          <NavLink to="/" end>This week</NavLink>
          <NavLink to="/basket">Basket builder</NavLink>
          <NavLink to="/runs">Saved baskets</NavLink>
          <div className="group">Modules</div>
          <NavLink to="/pantry">Pantry</NavLink>
          <NavLink to="/rules">Item rules</NavLink>
          <NavLink to="/prices">Price comparison</NavLink>
          <NavLink to="/supermarkets">Supermarkets</NavLink>
          <div className="group">Integrations</div>
          <NavLink to="/agents">Agents (MCP)</NavLink>
        </nav>
        <div className="spacer" />
        <button className="link small" onClick={quit}>Quit app</button>
      </aside>
      <main className="main">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/basket" element={<BasketPage />} />
          <Route path="/runs" element={<RunsPage />} />
          <Route path="/runs/:id" element={<RunsPage />} />
          <Route path="/pantry" element={<PantryPage />} />
          <Route path="/rules" element={<RulesPage />} />
          <Route path="/prices" element={<PricesPage />} />
          <Route path="/supermarkets" element={<SupermarketsPage />} />
          <Route path="/agents" element={<AgentsPage />} />
        </Routes>
      </main>
    </div>
  );
}
