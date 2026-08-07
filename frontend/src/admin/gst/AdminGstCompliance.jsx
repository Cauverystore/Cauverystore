import React, { useState, useEffect, useCallback } from "react";
import api from "../../api/axios";

const card = { background: "#fff", border: "1px solid #e5e7eb", borderRadius: 10, padding: "1.25rem" };
const inp = { width: "100%", padding: "0.5rem", border: "1px solid #d1d5db", borderRadius: 6, fontSize: "0.85rem" };
const lbl = { fontSize: "0.8rem", fontWeight: 500, display: "block", marginBottom: 4 };
const hint = { fontSize: "0.75rem", color: "#6b7280", marginTop: 3 };
const btn = { padding: "0.5rem 1rem", border: "none", borderRadius: 6, cursor: "pointer", fontSize: "0.85rem", fontWeight: 500 };

/**
 * The marketplace's own tax registration, and whether the store can charge correct tax today.
 *
 * There is no fallback rate any more: a product whose rate cannot be determined refuses to
 * sell. That is the right behaviour and it is also unforgiving, so this screen exists to show
 * the seller exactly what would fail before a customer meets it at checkout.
 */
const AdminGstCompliance = () => {
  const [readiness, setReadiness] = useState(null);
  const [config, setConfig] = useState(null);
  const [form, setForm] = useState({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [r, c] = await Promise.all([
        api.get("/api/admin/gst/readiness"),
        api.get("/api/gst/configurations").catch(() => ({ data: [] })),
      ]);
      setReadiness(r.data);
      const existing = Array.isArray(c.data) ? c.data[0] : c.data?.configurations?.[0];
      setConfig(existing || null);
      setForm(existing || {});
      setError("");
    } catch {
      setError("Could not load the compliance status.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  // CBIC's notification API needs credentials nobody here has, so this cannot be polled. The
  // honest substitute is a named, dated check - which is what this records.
  const confirmRatesCurrent = async () => {
    const note = window.prompt(
      "Confirm the GST rates are current.\n\n" +
      "Check taxinformation.cbic.gov.in for any Central Tax (Rate) notification issued since " +
      (readiness?.rateFreshness?.newestNotification || "the ones applied") + ".\n\n" +
      "Anything to note about the check?");
    if (note === null) return;
    setVerifying(true);
    setError("");
    try {
      await api.post("/api/admin/gst/rate-freshness/verify", { note });
      setNotice("Recorded. The rates are confirmed current as of today.");
      await load();
    } catch (err) {
      setError(err?.response?.data?.error || "The verification could not be recorded.");
    } finally {
      setVerifying(false);
    }
  };

  const save = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await api.post("/api/gst/configurations", { ...config, ...form });
      setNotice("Saved. Re-checking compliance…");
      await load();
    } catch (err) {
      // The backend rejects a GSTIN that fails its checksum, a TCS registration that duplicates
      // the regular one, a malformed CIN or IFSC. Those messages say exactly what is wrong, so
      // they are shown as-is rather than replaced with a generic failure.
      setError(err?.response?.data?.error || err?.response?.data?.message
        || "The settings could not be saved.");
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <p style={{ color: "#6b7280" }}>Loading…</p>;

  const ready = readiness?.ready;
  const blockers = readiness?.blockingProducts || [];
  const gaps = readiness?.marketplaceGaps || [];
  const badInvoices = readiness?.invoicesTaxedByFallback || [];
  const freshness = readiness?.rateFreshness;
  const pendingNotifications = readiness?.pendingRateNotifications || [];
  const freshnessTone = {
    CURRENT: { border: "#bbf7d0", bg: "#f0fdf4", fg: "#15803d" },
    DUE: { border: "#fde68a", bg: "#fffbeb", fg: "#92400e" },
    OVERDUE: { border: "#fecaca", bg: "#fef2f2", fg: "#b91c1c" },
    NEVER_VERIFIED: { border: "#fecaca", bg: "#fef2f2", fg: "#b91c1c" },
  }[freshness?.status] || { border: "#e5e7eb", bg: "#fff", fg: "#374151" };

  return (
    <div>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>GST Compliance</h1>
      <p style={{ margin: "4px 0 1.5rem", color: "#6b7280", fontSize: "0.85rem" }}>
        There is no fallback tax rate. A product whose rate cannot be determined will not sell,
        so anything listed here has to be settled before it can.
      </p>

      {error && (
        <div style={{ ...card, borderColor: "#fecaca", background: "#fef2f2", color: "#b91c1c", marginBottom: "1rem", fontSize: "0.85rem" }}>
          {error}
        </div>
      )}
      {notice && (
        <div style={{ ...card, borderColor: "#bbf7d0", background: "#f0fdf4", color: "#15803d", marginBottom: "1rem", fontSize: "0.85rem" }}>
          {notice}
        </div>
      )}

      <div style={{
        ...card,
        borderColor: ready ? "#bbf7d0" : "#fde68a",
        background: ready ? "#f0fdf4" : "#fffbeb",
        marginBottom: "1.5rem",
      }}>
        <div style={{ fontWeight: 700, fontSize: "1.05rem", color: ready ? "#15803d" : "#92400e" }}>
          {ready ? "Ready to charge tax correctly" : "Not ready"}
        </div>
        <p style={{ margin: "6px 0 0", fontSize: "0.88rem", color: "#374151" }}>
          {readiness?.summary}
        </p>
      </div>

      {freshness && (
        <div style={{ ...card, borderColor: freshnessTone.border, background: freshnessTone.bg, marginBottom: "1.5rem" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem", flexWrap: "wrap", alignItems: "flex-start" }}>
            <div style={{ flex: "1 1 22rem" }}>
              <h3 style={{ marginTop: 0, marginBottom: 4, fontSize: "1.05rem", fontWeight: 600, color: freshnessTone.fg }}>
                {freshness.status === "CURRENT" ? "Rates confirmed current"
                  : freshness.status === "DUE" ? "Rate check is due"
                  : freshness.status === "OVERDUE" ? "Rate check is overdue"
                  : "Rates have never been checked"}
              </h3>
              <p style={{ margin: 0, fontSize: "0.87rem", color: "#374151" }}>{freshness.message}</p>
              {freshness.lastVerifiedBy && (
                <p style={{ ...hint, marginTop: 6 }}>
                  Last confirmed by {freshness.lastVerifiedBy}
                  {freshness.daysSinceVerified != null && ` — ${freshness.daysSinceVerified} day(s) ago`}
                </p>
              )}
              <p style={{ ...hint, marginTop: 6 }}>{freshness.checkAt}</p>
            </div>
            <button type="button" onClick={confirmRatesCurrent} disabled={verifying}
                    style={{ ...btn, background: "#0f172a", color: "#fff", whiteSpace: "nowrap" }}>
              {verifying ? "Recording…" : "I have checked CBIC"}
            </button>
          </div>

          {pendingNotifications.length > 0 && (
            <div style={{ marginTop: "0.9rem", paddingTop: "0.8rem", borderTop: "1px solid rgba(0,0,0,0.08)" }}>
              <div style={{ fontWeight: 600, fontSize: "0.9rem", color: "#b91c1c" }}>
                Published but not applied
              </div>
              <ul style={{ fontSize: "0.85rem", color: "#374151", paddingLeft: "1.1rem", marginTop: 4 }}>
                {pendingNotifications.map((n) => (
                  <li key={n.notificationNumber} style={{ marginBottom: 3 }}>
                    <strong>{n.notificationNumber}</strong>
                    {n.effectiveFrom && ` — effective ${n.effectiveFrom}`}
                    {n.description && `. ${n.description}`}
                  </li>
                ))}
              </ul>
              <p style={{ ...hint, marginTop: 4 }}>
                Invoices are being raised at superseded rates until the rate seed is regenerated
                from these.
              </p>
            </div>
          )}
        </div>
      )}

      {gaps.length > 0 && (
        <div style={{ ...card, marginBottom: "1.5rem" }}>
          <h3 style={{ marginTop: 0, fontSize: "1.05rem", fontWeight: 600 }}>
            Marketplace registration
          </h3>
          <ul style={{ fontSize: "0.87rem", color: "#92400e", paddingLeft: "1.1rem" }}>
            {gaps.map((g, i) => <li key={i} style={{ marginBottom: 4 }}>{g}</li>)}
          </ul>
        </div>
      )}

      <form onSubmit={save} style={{ ...card, marginBottom: "1.5rem" }}>
        <h3 style={{ marginTop: 0, fontSize: "1.05rem", fontWeight: 600 }}>Your registration</h3>
        <p style={{ ...hint, marginBottom: "1rem" }}>
          Printed on every invoice the marketplace issues and used to file its returns.
        </p>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill,minmax(230px,1fr))", gap: "1rem" }}>
          <div>
            <label style={lbl}>Legal entity name</label>
            <input name="legalName" value={form.legalName || ""} onChange={change} style={inp} />
          </div>
          <div>
            <label style={lbl}>GSTIN</label>
            <input name="gstin" value={form.gstin || ""} onChange={change} style={inp} placeholder="33AABCC1234D1Z5" />
            <span style={hint}>Your regular registration. Checked against its check digit.</span>
          </div>
          <div>
            <label style={lbl}>TCS GSTIN (section 52)</label>
            <input name="tcsGstin" value={form.tcsGstin || ""} onChange={change} style={inp} />
            <span style={hint}>
              A <strong>separate</strong> registration from the one above. GSTR-8 filed under the
              regular GSTIN is rejected, so TCS cannot be filed without it.
            </span>
          </div>
          <div>
            <label style={lbl}>CIN</label>
            <input name="cin" value={form.cin || ""} onChange={change} style={inp} placeholder="U52100TN2025PTC167842" />
          </div>
          <div>
            <label style={lbl}>PAN</label>
            <input name="pan" value={form.pan || ""} onChange={change} style={inp} />
          </div>
          <div>
            <label style={lbl}>State code</label>
            <input name="stateCode" value={form.stateCode || ""} onChange={change} style={inp} placeholder="33" />
            <span style={hint}>Decides CGST+SGST (or UTGST for Union Territories without a legislature) vs IGST on commission invoices.</span>
          </div>
        </div>

        <h4 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "1.4rem 0 0.2rem" }}>
          Nodal / escrow account
        </h4>
        <p style={{ ...hint, marginBottom: "0.8rem" }}>
          Money collected on behalf of sellers is held here, not owned. Keeping it separate from
          your own account is what makes settlement reconcilable.
        </p>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill,minmax(230px,1fr))", gap: "1rem" }}>
          <div><label style={lbl}>Bank</label><input name="nodalBankName" value={form.nodalBankName || ""} onChange={change} style={inp} /></div>
          <div><label style={lbl}>Account number</label><input name="nodalAccountNumber" value={form.nodalAccountNumber || ""} onChange={change} style={inp} /></div>
          <div><label style={lbl}>IFSC</label><input name="nodalIfsc" value={form.nodalIfsc || ""} onChange={change} style={inp} placeholder="HDFC0001234" /></div>
        </div>

        <button type="submit" disabled={saving} style={{ ...btn, background: "#16a34a", color: "#fff", marginTop: "1.2rem" }}>
          {saving ? "Saving…" : "Save registration"}
        </button>
      </form>

      {blockers.length > 0 && (
        <div style={{ ...card, marginBottom: "1.5rem" }}>
          <h3 style={{ marginTop: 0, fontSize: "1.05rem", fontWeight: 600 }}>
            Products that cannot be sold ({blockers.length})
          </h3>
          <p style={{ ...hint, marginBottom: "0.8rem" }}>
            Each of these is on sale but has no determinable rate. Until it is settled, an order
            containing it will be refused.
          </p>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem", minWidth: 620 }}>
              <thead>
                <tr style={{ textAlign: "left", color: "#6b7280", fontSize: "0.72rem" }}>
                  <th style={th}>Product</th><th style={th}>HSN</th><th style={th}>Problem</th><th style={th}>What to do</th>
                </tr>
              </thead>
              <tbody>
                {blockers.map((b) => (
                  <tr key={b.productId} style={{ borderTop: "1px solid #f3f4f6" }}>
                    <td style={td}>{b.productName}</td>
                    <td style={{ ...td, fontFamily: "monospace" }}>{b.hsnCode || "—"}</td>
                    <td style={td}>{b.problem}</td>
                    <td style={{ ...td, color: "#15803d" }}>{b.fix}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {badInvoices.length > 0 && (
        <div style={{ ...card, borderColor: "#fecaca" }}>
          <h3 style={{ marginTop: 0, fontSize: "1.05rem", fontWeight: 600, color: "#b91c1c" }}>
            Invoices raised at the old fallback rate ({badInvoices.length})
          </h3>
          <p style={{ ...hint, marginBottom: "0.8rem" }}>
            These were taxed at a rate that was never lawful for those goods, before the fallback
            was removed. Each needs a credit note, and your accountant should see this list.
          </p>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem", minWidth: 480 }}>
              <thead>
                <tr style={{ textAlign: "left", color: "#6b7280", fontSize: "0.72rem" }}>
                  <th style={th}>Invoice</th><th style={th}>Date</th><th style={th}>Seller GSTIN</th><th style={th}>Tax charged</th>
                </tr>
              </thead>
              <tbody>
                {badInvoices.map((i) => (
                  <tr key={i.invoiceId} style={{ borderTop: "1px solid #f3f4f6" }}>
                    <td style={td}>{i.invoiceNumber}</td>
                    <td style={td}>{i.invoiceDate}</td>
                    <td style={{ ...td, fontFamily: "monospace" }}>{i.sellerGstin}</td>
                    <td style={{ ...td, fontVariantNumeric: "tabular-nums" }}>₹{i.totalTax}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};

const th = { padding: "0.5rem 0.6rem 0.5rem 0", fontWeight: 500 };
const td = { padding: "0.55rem 0.6rem 0.55rem 0", verticalAlign: "top" };

export default AdminGstCompliance;
