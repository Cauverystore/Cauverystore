import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { ArrowLeft, Printer, Download, Loader } from "lucide-react";
import api from "../api/axios";

const GST_INVOICE_STYLES = `
  .giv-page { max-width: 900px; margin: 0 auto; padding: 1.5rem; }
  .giv-toolbar { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 1.5rem; flex-wrap: wrap; }
  .giv-back { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.45rem 0.9rem; border: 1.5px solid #e2e8f0; border-radius: 8px; background: #fff; color: #475569; font-size: 0.85rem; font-weight: 500; cursor: pointer; text-decoration: none; }
  .giv-back:hover { background: #f8fafc; }
  .giv-btn { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.45rem 0.9rem; border-radius: 8px; font-size: 0.85rem; font-weight: 600; border: none; cursor: pointer; transition: all 0.2s; white-space: nowrap; }
  .giv-btn-primary { background: #0E5C5C; color: #fff; }
  .giv-btn-primary:hover { background: #0a4a4a; }
  .giv-btn-outline { background: #fff; color: #0E5C5C; border: 1.5px solid #0E5C5C; }
  .giv-btn-outline:hover { background: #f0fdf4; }

  .giv-invoice { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); overflow: hidden; }
  .giv-header { padding: 1.5rem 2rem; border-bottom: 2px solid #0E5C5C; display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; }
  .giv-header-left { display: flex; align-items: center; gap: 1rem; }
  .giv-header-left img { height: 2.5rem; width: auto; }
  .giv-brand-name { font-size: 0.95rem; font-weight: 700; color: #0E5C5C; line-height: 1.2; }
  .giv-brand-tagline { font-size: 0.68rem; font-weight: 600; color: #C8A24B; margin-bottom: 0.25rem; }
  .giv-header-left h1 { font-size: 1.1rem; font-weight: 700; color: #0f172a; margin: 0; }
  .giv-header-right { text-align: right; }
  .giv-header-right .giv-inv-number { font-size: 1rem; font-weight: 700; color: #0E5C5C; }
  .giv-header-right .giv-inv-label { font-size: 0.78rem; color: #64748b; margin-bottom: 0.15rem; }
  .giv-header-right .giv-inv-status { font-size: 0.72rem; padding: 2px 10px; border-radius: 999px; font-weight: 600; display: inline-block; margin-top: 0.25rem; }
  .giv-status-synced { background: #dcfce7; color: #166534; }
  .giv-status-generated { background: #fef9c3; color: #854d0e; }
  .giv-status-failed { background: #fee2e2; color: #991b1b; }
  .giv-status-draft { background: #f1f5f9; color: #475569; }

  .giv-body { padding: 1.5rem 2rem; }
  .giv-parties { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; margin-bottom: 1.5rem; }
  .giv-party-box { padding: 1rem; border: 1px solid #e2e8f0; border-radius: 8px; background: #f8fafc; }
  .giv-party-box h3 { font-size: 0.8rem; font-weight: 700; color: #0E5C5C; text-transform: uppercase; letter-spacing: 0.3px; margin: 0 0 0.5rem; padding-bottom: 0.35rem; border-bottom: 1px solid #e2e8f0; }
  .giv-party-box .giv-party-name { font-size: 0.95rem; font-weight: 600; color: #0f172a; }
  .giv-party-box .giv-party-detail { font-size: 0.82rem; color: #475569; line-height: 1.5; }
  .giv-party-box .giv-party-gstin { font-size: 0.8rem; font-family: monospace; font-weight: 600; color: #0f172a; margin-top: 0.35rem; }

  .giv-identifiers { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 0.75rem; margin-bottom: 1.5rem; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 0.75rem 1rem; }
  .giv-id-item {}
  .giv-id-label { font-size: 0.7rem; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.3px; }
  .giv-id-value { font-size: 0.85rem; font-weight: 600; color: #0f172a; margin-top: 0.1rem; }

  .giv-items { margin-bottom: 1.5rem; overflow-x: auto; }
  .giv-items table { width: 100%; border-collapse: collapse; font-size: 0.82rem; }
  .giv-items th { background: #0E5C5C; color: #fff; padding: 0.6rem 0.75rem; text-align: left; font-weight: 600; white-space: nowrap; font-size: 0.78rem; }
  .giv-items th:first-child { border-radius: 6px 0 0 0; }
  .giv-items th:last-child { border-radius: 0 6px 0 0; }
  .giv-items td { padding: 0.55rem 0.75rem; border-bottom: 1px solid #f1f5f9; color: #1e293b; }
  .giv-items tr:nth-child(even) td { background: #f8fafc; }
  .giv-items .giv-hsn { font-family: monospace; font-size: 0.78rem; color: #64748b; }
  .giv-items .giv-amt { text-align: right; font-family: monospace; white-space: nowrap; }
  .giv-items .giv-amt-total { font-weight: 700; color: #0E5C5C; }

  .giv-summary { display: flex; justify-content: flex-end; margin-bottom: 1.5rem; }
  .giv-summary-table { width: 350px; border-collapse: collapse; font-size: 0.85rem; }
  .giv-summary-table td { padding: 0.4rem 0.75rem; border-bottom: 1px solid #f1f5f9; }
  .giv-summary-table td:first-child { color: #64748b; }
  .giv-summary-table td:last-child { text-align: right; font-weight: 600; font-family: monospace; }
  .giv-summary-table .giv-sum-total { font-size: 1rem; font-weight: 800; color: #0E5C5C; border-top: 2px solid #0E5C5C; }
  .giv-summary-table .giv-sum-total td { padding: 0.6rem 0.75rem; }

  .giv-tax-breakup { margin-bottom: 1.5rem; }
  .giv-tax-breakup h3 { font-size: 0.8rem; font-weight: 700; color: #0E5C5C; text-transform: uppercase; letter-spacing: 0.3px; margin: 0 0 0.5rem; }
  .giv-tax-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 0.6rem; }
  .giv-tax-card { border: 1px solid #e2e8f0; border-radius: 6px; padding: 0.6rem 0.75rem; background: #f8fafc; text-align: center; }
  .giv-tax-card .giv-tax-label { font-size: 0.72rem; color: #64748b; }
  .giv-tax-card .giv-tax-value { font-size: 0.95rem; font-weight: 700; color: #0f172a; margin-top: 0.15rem; }

  .giv-amount-words { font-size: 0.85rem; color: #475569; margin-bottom: 1.5rem; padding: 0.75rem 1rem; border: 1px solid #e2e8f0; border-radius: 8px; background: #fffbeb; }
  .giv-amount-words strong { color: #0E5C5C; }

  .giv-qr-section { display: flex; gap: 1.5rem; align-items: flex-start; margin-bottom: 1.5rem; padding: 1rem; border: 1px solid #e2e8f0; border-radius: 8px; background: #f8fafc; }
  .giv-qr-placeholder { width: 120px; height: 120px; border: 2px dashed #cbd5e1; border-radius: 8px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; background: #fff; }
  .giv-qr-placeholder span { font-size: 0.72rem; color: #94a3b8; text-align: center; padding: 0.5rem; }
  .giv-qr-info { flex: 1; min-width: 0; }
  .giv-qr-info .giv-qr-label { font-size: 0.72rem; color: #94a3b8; }
  .giv-qr-info .giv-qr-value { font-size: 0.82rem; font-weight: 600; color: #0f172a; word-break: break-all; margin-bottom: 0.25rem; }

  .giv-declaration { font-size: 0.78rem; color: #64748b; line-height: 1.6; padding: 0.75rem 1rem; border-top: 1px solid #e2e8f0; }
  .giv-footer { text-align: center; padding: 1rem 2rem; border-top: 1px solid #e2e8f0; font-size: 0.75rem; color: #94a3b8; }

  @media print {
    .giv-toolbar, .giv-back { display: none !important; }
    .giv-page { padding: 0; }
    .giv-invoice { border: none; box-shadow: none; border-radius: 0; }
    .giv-header { border-bottom-width: 2px; }
    .giv-body { padding: 1rem 1.5rem; }
  }
  @media (max-width: 768px) {
    .giv-parties { grid-template-columns: 1fr; }
    .giv-summary-table { width: 100%; }
    .giv-qr-section { flex-direction: column; align-items: center; }
  }
`;

const numberToWords = (num) => {
  if (!num || isNaN(num)) return "Zero";
  const a = ["", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"];
  const b = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];
  const fn = (n) => {
    if (n < 20) return a[n];
    if (n < 100) return b[Math.floor(n / 10)] + (n % 10 ? " " + a[n % 10] : "");
    if (n < 1000) return a[Math.floor(n / 100)] + " Hundred" + (n % 100 ? " " + fn(n % 100) : "");
    if (n < 100000) return fn(Math.floor(n / 1000)) + " Thousand" + (n % 1000 ? " " + fn(n % 1000) : "");
    if (n < 10000000) return fn(Math.floor(n / 100000)) + " Lakh" + (n % 100000 ? " " + fn(n % 100000) : "");
    return fn(Math.floor(n / 10000000)) + " Crore" + (n % 10000000 ? " " + fn(n % 10000000) : "");
  };
  const whole = Math.floor(num);
  const decimal = Math.round((num - whole) * 100);
  let result = fn(whole) + " Rupees";
  if (decimal > 0) result += " and " + fn(decimal) + " Paise";
  return result + " Only";
};

const GstInvoiceView = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [invoice, setInvoice] = useState(null);
  const [configs, setConfigs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [pdfLoading, setPdfLoading] = useState(false);

  const handleDownloadPdf = async () => {
    setPdfLoading(true);
    try {
      const res = await api.get(`/api/gst/invoice/${id}/pdf`, { responseType: "blob" });
      const blob = new Blob([res.data], { type: "application/pdf" });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `invoice-${id}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      if (err.response?.status === 403) setError("Access denied: You do not have permission to download this invoice.");
      else setError("Failed to download PDF.");
    }
    setPdfLoading(false);
  };

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [invRes, cfgRes] = await Promise.allSettled([
          api.get(`/api/gst/invoice/${id}`),
          api.get("/api/gst/configurations"),
        ]);
        if (invRes.status === "fulfilled") setInvoice(invRes.value.data);
        else if (invRes.reason?.response?.status === 403) setError("Access denied: You do not have permission to view this invoice.");
        else setError("Failed to load invoice");
        if (cfgRes.status === "fulfilled") setConfigs(cfgRes.value.data.configurations || []);
      } catch { setError("Failed to load invoice"); }
      setLoading(false);
    };
    fetchData();
  }, [id]);

  const sellerConfig = configs.find((c) => c.gstin === invoice?.sellerGstin);
  const taxRate = invoice?.isInterState
    ? (invoice?.igstRate || invoice?.cgstRate || 0) + (invoice?.sgstRate || 0)
    : (invoice?.cgstRate || 0) + (invoice?.sgstRate || 0);

  if (loading) return <div className="giv-page" style={{ textAlign: "center", padding: "3rem", color: "#94a3b8" }}>Loading invoice...</div>;
  if (error) return <div className="giv-page" style={{ textAlign: "center", padding: "3rem", color: "#dc2626" }}>{error}</div>;
  if (!invoice) return <div className="giv-page" style={{ textAlign: "center", padding: "3rem", color: "#94a3b8" }}>Invoice not found.</div>;

  const statusClass = invoice.status === "SYNCED" ? "giv-status-synced" : invoice.status === "SYNC_FAILED" ? "giv-status-failed" : invoice.status === "GENERATED" ? "giv-status-generated" : "giv-status-draft";

  return (
    <>
      <style>{GST_INVOICE_STYLES}</style>
      <div className="giv-page">
        <div className="giv-toolbar">
          <button className="giv-back" onClick={() => navigate(-1)}><ArrowLeft size={16} /> Back</button>
          <button className="giv-btn giv-btn-primary" onClick={() => window.print()}><Printer size={16} /> Print</button>
          <button className="giv-btn giv-btn-outline" onClick={handleDownloadPdf} disabled={pdfLoading}>
            {pdfLoading ? <Loader size={16} /> : <Download size={16} />} {pdfLoading ? "Downloading..." : "Download PDF"}
          </button>
        </div>

        <div className="giv-invoice" id="gst-invoice-print">
          <div className="giv-header">
            <div className="giv-header-left">
              <img src="/images/logo.jpg" alt="Cauvery Store" />
              <div>
                <div className="giv-brand-name">Cauvery Store</div>
                <div className="giv-brand-tagline">Everyday Essentials, Delivered</div>
                <h1>Tax Invoice</h1>
                <div style={{ fontSize: "0.78rem", color: "#64748b" }}>
                  {invoice.invoiceCopyType === "ORIGINAL" ? "Original for Recipient" :
                   invoice.invoiceCopyType === "DUPLICATE" ? "Duplicate for Transporter" :
                   invoice.invoiceCopyType === "TRIPLICATE" ? "Triplicate for Supplier" : "Original for Recipient"}
                  {invoice.supplyType === "GOODS" ? " (Goods)" : " (Services)"}
                  {" | "}{invoice.isInterState ? "Inter-State" : "Intra-State"}
                </div>
              </div>
            </div>
            <div className="giv-header-right">
              <div className="giv-inv-label">Invoice No.</div>
              <div className="giv-inv-number">{invoice.invoiceNumber}</div>
              <div className="giv-inv-label" style={{ marginTop: "0.3rem" }}>Invoice Date</div>
              <div style={{ fontSize: "0.88rem", fontWeight: 600 }}>{invoice.invoiceDate}</div>
              <span className={`giv-inv-status ${statusClass}`}>{invoice.status}</span>
            </div>
          </div>

          <div className="giv-body">
            <div className="giv-parties">
              <div className="giv-party-box">
                <h3>Seller (Supplier)</h3>
                <div className="giv-party-name">{invoice.sellerLegalName || sellerConfig?.legalName || "Cauvery Store"}</div>
                <div className="giv-party-detail">{invoice.sellerAddress || sellerConfig?.address || ""}</div>
                <div className="giv-party-gstin">GSTIN: {invoice.sellerGstin}</div>
                {sellerConfig?.stateName && <div className="giv-party-detail" style={{ marginTop: "0.2rem", fontSize: "0.78rem" }}>{sellerConfig.stateName} ({sellerConfig.stateCode})</div>}
              </div>
              <div className="giv-party-box">
                <h3>Buyer (Recipient)</h3>
                <div className="giv-party-name">{invoice.buyerName}</div>
                <div className="giv-party-detail">{invoice.buyerAddress}</div>
                <div className="giv-party-gstin">{invoice.buyerGstin === "URP" ? "GSTIN: URP (Unregistered Person)" : "GSTIN: " + invoice.buyerGstin}</div>
                {invoice.buyerGstin && invoice.buyerGstin !== "URP" && <div className="giv-party-detail" style={{ marginTop: "0.2rem", fontSize: "0.78rem", color: "#0E5C5C" }}>ITC Eligible: Yes (B2B)</div>}
              </div>
            </div>

            <div className="giv-identifiers">
              <div className="giv-id-item"><div className="giv-id-label">Place of Supply</div><div className="giv-id-value">{invoice.placeOfSupply}</div></div>
              <div className="giv-id-item"><div className="giv-id-label">Supply Type</div><div className="giv-id-value">{invoice.isInterState ? "Inter-State (IGST)" : "Intra-State (CGST+SGST)"}</div></div>
              <div className="giv-id-item"><div className="giv-id-label">Invoice Type</div><div className="giv-id-value">{invoice.invoiceType || "B2C"}</div></div>
              <div className="giv-id-item"><div className="giv-id-label">Copy</div><div className="giv-id-value">
                {invoice.invoiceCopyType === "ORIGINAL" ? "Original" :
                 invoice.invoiceCopyType === "DUPLICATE" ? "Duplicate" :
                 invoice.invoiceCopyType === "TRIPLICATE" ? "Triplicate" : "Original"}
              </div></div>
              <div className="giv-id-item"><div className="giv-id-label">HSN Digits</div><div className="giv-id-value">{invoice.hsnDigits || 4}-digit HSN</div></div>
              <div className="giv-id-item"><div className="giv-id-label">Reverse Charge</div><div className="giv-id-value" style={{ color: invoice.reverseCharge ? "#D93A2A" : "#0E5C5C" }}>{invoice.reverseCharge ? "Applicable" : "Not Applicable"}</div></div>
              <div className="giv-id-item"><div className="giv-id-label">IRN</div><div className="giv-id-value" style={{ fontSize: "0.75rem" }}>{invoice.irn || "Not generated"}</div></div>
              {invoice.ewayBillNumber && <div className="giv-id-item"><div className="giv-id-label">E-Way Bill</div><div className="giv-id-value">{invoice.ewayBillNumber}{invoice.ewayBillExpiry ? " (exp: " + invoice.ewayBillExpiry + ")" : ""}</div></div>}
              <div className="giv-id-item"><div className="giv-id-label">Ack No.</div><div className="giv-id-value" style={{ fontSize: "0.75rem" }}>{invoice.ackNo || "-"}</div></div>
              <div className="giv-id-item"><div className="giv-id-label">Ack Date</div><div className="giv-id-value">{invoice.ackDate || "-"}</div></div>
            </div>

            <div className="giv-items">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>HSN/SAC</th>
                    <th>Description</th>
                    <th style={{ textAlign: "right" }}>Qty</th>
                    <th style={{ textAlign: "right" }}>Unit Price</th>
                    <th style={{ textAlign: "right" }}>Taxable Value</th>
                    {!invoice.isInterState && <><th style={{ textAlign: "right" }}>CGST</th><th style={{ textAlign: "right" }}>SGST</th></>}
                    {invoice.isInterState && <th style={{ textAlign: "right" }}>IGST</th>}
                    <th style={{ textAlign: "right" }}>Total</th>
                  </tr>
                </thead>
                <tbody>
                  {(invoice.items || []).map((item, i) => (
                    <tr key={i}>
                      <td>{i + 1}</td>
                      <td className="giv-hsn">{item.hsnCode || "-"}</td>
                      <td>{item.productName}</td>
                      <td className="giv-amt">{item.quantity}</td>
                      <td className="giv-amt">&#8377;{(item.unitPrice || 0).toFixed(2)}</td>
                      <td className="giv-amt">&#8377;{(item.taxableValue || 0).toFixed(2)}</td>
                      {!invoice.isInterState && <><td className="giv-amt">{(item.cgstRate || 0)}%<br />&#8377;{(item.cgstAmount || 0).toFixed(2)}</td><td className="giv-amt">{(item.sgstRate || 0)}%<br />&#8377;{(item.sgstAmount || 0).toFixed(2)}</td></>}
                      {invoice.isInterState && <td className="giv-amt">{(item.igstRate || 0)}%<br />&#8377;{(item.igstAmount || 0).toFixed(2)}</td>}
                      <td className="giv-amt giv-amt-total">&#8377;{(item.totalAmount || 0).toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="giv-tax-breakup">
              <h3>Tax Breakup</h3>
              <div className="giv-tax-grid">
                <div className="giv-tax-card">
                  <div className="giv-tax-label">Taxable Amount</div>
                  <div className="giv-tax-value">&#8377;{(invoice.taxableAmount || 0).toFixed(2)}</div>
                </div>
                {!invoice.isInterState ? (
                  <>
                    <div className="giv-tax-card">
                      <div className="giv-tax-label">CGST @ {(invoice.cgstRate || 0)}%</div>
                      <div className="giv-tax-value" style={{ color: "#2563eb" }}>&#8377;{(invoice.cgstAmount || 0).toFixed(2)}</div>
                    </div>
                    <div className="giv-tax-card">
                      <div className="giv-tax-label">SGST @ {(invoice.sgstRate || 0)}%</div>
                      <div className="giv-tax-value" style={{ color: "#7c3aed" }}>&#8377;{(invoice.sgstAmount || 0).toFixed(2)}</div>
                    </div>
                  </>
                ) : (
                  <div className="giv-tax-card">
                    <div className="giv-tax-label">IGST @ {(invoice.igstRate || invoice.cgstRate || 0)}%</div>
                    <div className="giv-tax-value" style={{ color: "#d97706" }}>&#8377;{(invoice.igstAmount || 0).toFixed(2)}</div>
                  </div>
                )}
                <div className="giv-tax-card">
                  <div className="giv-tax-label">Total Tax</div>
                  <div className="giv-tax-value">&#8377;{(invoice.totalTax || 0).toFixed(2)}</div>
                </div>
                <div className="giv-tax-card">
                  <div className="giv-tax-label">TCS @ {(invoice.tcsRate || 1)}%</div>
                  <div className="giv-tax-value" style={{ color: "#dc2626" }}>&#8377;{(invoice.tcsAmount || 0).toFixed(2)}</div>
                </div>
              </div>
            </div>

            <div className="giv-summary">
              <table className="giv-summary-table">
                <tbody>
                  <tr><td>Taxable Amount</td><td>&#8377;{(invoice.taxableAmount || 0).toFixed(2)}</td></tr>
                  {!invoice.isInterState && <><tr><td>CGST</td><td style={{ color: "#2563eb" }}>&#8377;{(invoice.cgstAmount || 0).toFixed(2)}</td></tr><tr><td>SGST</td><td style={{ color: "#7c3aed" }}>&#8377;{(invoice.sgstAmount || 0).toFixed(2)}</td></tr></>}
                  {invoice.isInterState && <tr><td>IGST</td><td style={{ color: "#d97706" }}>&#8377;{(invoice.igstAmount || 0).toFixed(2)}</td></tr>}
                  <tr><td>Total Tax</td><td>&#8377;{(invoice.totalTax || 0).toFixed(2)}</td></tr>
                  <tr><td>TCS @ {(invoice.tcsRate || 1)}%</td><td style={{ color: "#dc2626" }}>&#8377;{(invoice.tcsAmount || 0).toFixed(2)}</td></tr>
                  <tr className="giv-sum-total"><td>Total Amount</td><td>&#8377;{(invoice.totalAmount || 0).toFixed(2)}</td></tr>
                </tbody>
              </table>
            </div>

            <div className="giv-amount-words">
              <strong>Amount in Words:</strong> {numberToWords(invoice.totalAmount || 0)}
            </div>

            {(invoice.irn || invoice.qrCode) && (
              <div className="giv-qr-section">
                <div className="giv-qr-placeholder">
                  {invoice.qrCode ? (
                    <img src={invoice.qrCode} alt="QR Code" style={{ width: 120, height: 120 }} />
                  ) : (
                    <span>QR Code<br />not available</span>
                  )}
                </div>
                <div className="giv-qr-info">
                  <div className="giv-qr-label">IRN (Invoice Reference Number)</div>
                  <div className="giv-qr-value">{invoice.irn}</div>
                  <div className="giv-qr-label">Ack No.</div>
                  <div className="giv-qr-value">{invoice.ackNo || "-"}</div>
                  <div className="giv-qr-label">Ack Date</div>
                  <div className="giv-qr-value">{invoice.ackDate || "-"}</div>
                  {sellerConfig?.einvoiceEndpoint && <div style={{ fontSize: "0.72rem", color: "#94a3b8", marginTop: "0.35rem" }}>E-invoice generated via {sellerConfig.einvoiceEndpoint}</div>}
                  <div style={{ fontSize: "0.72rem", color: "#16a34a", marginTop: "0.25rem" }}>E-invoicing compliance: Enabled (turnover &gt; &#8377;5 Cr)</div>
                </div>
              </div>
            )}

            <div className="giv-declaration">
              <strong>Declaration (Rule 46 CGST Rules, 2017):</strong> We declare that this invoice shows the actual price of the goods/services described and that all particulars are true and correct.
              <ul style={{ margin: "0.35rem 0 0", paddingLeft: "1.25rem", fontSize: "0.78rem" }}>
                <li>This is a computer-generated {invoice.invoiceCopyType === "ORIGINAL" ? "Original" : invoice.invoiceCopyType === "DUPLICATE" ? "Duplicate" : "Triplicate"} invoice for {invoice.supplyType === "GOODS" ? "goods" : "services"} as per Rule 46.</li>
                <li>Invoice issued before removal/delivery of {invoice.supplyType === "GOODS" ? "goods" : "services"}.</li>
                <li>{invoice.isInterState ? "IGST charged (inter-state supply)." : "CGST + SGST charged (intra-state supply)."}</li>
                {invoice.reverseCharge && <li>Reverse Charge: Applicable — tax payable by recipient.</li>}
                <li>HSN/SAC digits: {invoice.hsnDigits || 4} (as per turnover).</li>
                <li>This is a system-generated invoice and does not require a physical signature.</li>
              </ul>
            </div>
          </div>

          <div className="giv-footer">
            This is a computer-generated GST invoice | {invoice.invoiceNumber} | Generated on {new Date().toLocaleDateString()}
          </div>
        </div>
      </div>
    </>
  );
};

export default GstInvoiceView;
